(ns dienstplan.db
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [hikari-cp.core :as cp]
   [mount.core :as mount :refer [defstate]]
   [ragtime.jdbc :as ragtime-jdbc]
   [ragtime.repl :as ragtime-repl]
   )
  (:import
   (org.postgresql.util PGobject
                        PSQLException)))

;; System component

(defstate db
  :start
  (let [db-opts (:db config)
        datasource (cp/make-datasource db-opts)]
    {:datasource datasource})
  :stop
  (-> db :datasource cp/close-datasource))

;; Migrations

(defn get-migration-config []
  (do
    (mount/start
     #'dienstplan.config/config
     #'dienstplan.db/db)
    {:datastore  (ragtime-jdbc/sql-database db)
     :migrations (ragtime-jdbc/load-resources "migrations")}))

(defn migrate []
  (ragtime-repl/migrate (get-migration-config)))

(defn rollback []
  (ragtime-repl/rollback (get-migration-config)))

;; JSONB support
;; https://web.archive.org/web/20161024231548/http://hiim.tv/clojure/2014/05/15/clojure-postgres-json/

(defn value-to-json-pgobject [value]
  (doto (PGobject.)
    (.setType "jsonb")
      (.setValue (json/generate-string value))))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value))

  clojure.lang.IPersistentVector
  (sql-value [value] (value-to-json-pgobject value)))

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json/parse-string value)
        :else value))))

;; Business layer

(defn duty-get
  [channel rotation]
  (jdbc/query
     db
     ["SELECT
         r.id AS rota_id,
         r.description,
         m.name AS duty
       FROM rota AS r
       JOIN mention AS m ON m.rota_id = r.id
       WHERE
         1 = 1
         AND r.channel = ?
         AND r.name = ?
         AND m.duty IS TRUE"
      channel rotation]))

(defn rota-list-get
  [channel]
  (jdbc/query
     db
     ["SELECT
         r.name,
         r.created_on
       FROM rota AS r
       WHERE
         1 = 1
         AND r.channel = ?
       ORDER BY r.created_on DESC
       LIMIT 500"
      channel]))

(defn rota-delete!
  [channel rotation]
  (jdbc/delete! db :rota ["channel = ? AND name = ?" channel rotation]))

(defn rota-insert!
  [params]
  (jdbc/with-db-transaction [conn db]
    (let [result
          (try
            (let [rota-insert
                  (jdbc/insert! conn :rota (:rota params) {:return-keys ["id"]})
                  rota-id (:id (first rota-insert))
                  mention-params (map #(assoc % :rota_id rota-id) (:mention params))
                  _ (jdbc/insert-multi! conn :mention mention-params)]
              {:ok true})
            (catch PSQLException e
              (let [message (.getMessage e)
                    duplicate? (string/includes? (.toLowerCase message) "duplicate key")
                    reason (cond duplicate? :duplicate :else :other)
                    error {:error {:reason reason :message message}}]
                error)))]
      result)))

(defn rotate-users
  [users]
  (let [current-duty-idx
        (if (empty? users)
          :empty
          (->> users
               (keep-indexed #(if (= (:duty %2) true) %1))
               first))
        next-duty-idx
        (cond
          (= current-duty-idx :empty) :list-empty
          (nil? current-duty-idx) :idx-not-found
          :else (mod (+ current-duty-idx 1) (count users)))
        result
        (if (contains? #{:list-empty :idx-not-found} next-duty-idx)
          (do
            (log/error (format "Cannot rotate users: %s" users))
            users)
          (into []
                (map-indexed
                 #(if (= %1 next-duty-idx)
                    (update %2 :duty (constantly true))
                    (update %2 :duty (constantly false))) users)))]
    result))

(defn rotate-duty!
  [channel rotation ts]
  (jdbc/with-db-transaction [conn db]
    (let [users
          (into
           []
           (jdbc/query
            conn
            ["SELECT
                   m.id,
                   m.rota_id,
                   m.name AS user,
                   m.duty
                 FROM rota AS r
                 JOIN mention AS m ON r.id = m.rota_id
                 WHERE
                   1 = 1
                   AND r.channel = ?
                   AND r.name = ?
                 ORDER BY r.id ASC
                 FOR UPDATE"
             channel rotation]))
          rotated (rotate-users users)]
      (do
        (doseq [user rotated]
          (log/info (jdbc/update!
                     conn :mention
                     {:duty (:duty user)}
                     ["id = ?" (:id user)])))
        (log/info (jdbc/update!
                   conn :rota
                   {:updated_on ts}
                   ["id = ?" (:rota_id (first users))]))))))

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
    (mount/start)
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

(defn rota-get
  [command-map]
  (let [channel (get-in command-map [:context :channel])
        name (get-in command-map [:args :name])]
    (jdbc/query db ["SELECT * FROM rota WHERE channel = ? AND name = ?" channel name])))

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

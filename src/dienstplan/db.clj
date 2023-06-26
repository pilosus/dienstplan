;; Copyright (c) Vitaly Samigullin and contributors. All rights reserved.
;;
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0.
;;
;; This Source Code may also be made available under the following Secondary
;; Licenses when the conditions for such availability set forth in the Eclipse
;; Public License, v. 2.0 are satisfied: GNU General Public License as published by
;; the Free Software Foundation, either version 2 of the License, or (at your
;; option) any later version, with the GNU Classpath Exception which is available
;; at https://www.gnu.org/software/classpath/license.html.
;;
;; SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

(ns dienstplan.db
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.sql :as sql]
   [next.jdbc.connection :as connection]
   [next.jdbc.result-set :as rs]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [honey.sql :as h]
   [mount.core :as mount :refer [defstate]]
   [ragtime.next-jdbc :as ragtime-jdbc]
   [ragtime.repl :as ragtime-repl])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.sql PreparedStatement)
   (org.postgresql.util PGobject
                        PSQLException)))

;; System component

(defstate db
  :start (connection/->pool HikariDataSource (:db config))
  ;; FIXME reflection warning
  :stop (-> db ^HikariDataSource .close))

;; Handling JSON/JSONB in PostgreSQL
;; https://github.com/seancorfield/next-jdbc/blob/develop/doc/tips-and-tricks.md#working-with-json-and-jsonb

(def ->json json/generate-string)
(def <-json #(json/parse-string % true))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure
  data."
  [^org.postgresql.util.PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (when value
        (with-meta (<-json value) {:pgtype type}))
      value)))

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))

;; Handling Date-related converions
;; https://github.com/seancorfield/next-jdbc/blob/develop/doc/result-set-builders.md#readablecolumn

(extend-protocol rs/ReadableColumn
  java.sql.Date
  (read-column-by-label [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index [^java.sql.Date v _2 _3]
    (.toLocalDate v))
  java.sql.Timestamp
  (read-column-by-label [^java.sql.Timestamp v _]
    (.toInstant v))
  (read-column-by-index [^java.sql.Timestamp v _2 _3]
    (.toInstant v)))

;; Migrations

(defn get-migration-config []
  (mount/start
   #'dienstplan.config/config
   #'dienstplan.db/db)
  {:datastore  (ragtime-jdbc/sql-database db)
   :migrations (ragtime-jdbc/load-resources "migrations")})

(defn migrate [_]
  (ragtime-repl/migrate (get-migration-config)))

(defn rollback [_]
  (ragtime-repl/rollback (get-migration-config)))

;; Business layer

(def sql-params {:checking :strict})

(defn duty-get
  "Get a single rotation map"
  [channel rotation]
  (jdbc/with-transaction [conn db]
    (jdbc/execute-one!
     conn
     (h/format
      {:select [[:r/id]
                :r/description
                [:m/name :duty]]
       :from [[:rota :r]]
       :join [[:mention :m] [:= :m.rota_id :r.id]]
       :where [:and
               [:is :m/duty true]
               [:= :r/channel channel]
               [:= :r/name rotation]]}
      sql-params))))

(defn rota-list-get
  [channel]
  (jdbc/with-transaction [conn db]
    (jdbc/execute!
     conn
     (h/format
      {:select [[:r/name]
                [[:date :r/created_on] :created_on]]
       :from [[:rota :r]]
       :where [[:= :r/channel channel]]
       :order-by [[:r/created_on :desc]]
       :limit 500}
      sql-params))))

(defn rota-about-get
  [channel rotation]
  (jdbc/with-transaction [conn db]
    (jdbc/execute-one!
     conn
     (h/format
      {:select [[[:date :r/created_on] :created_on]
                :r/description
                [[[:string_agg :m/name [:order-by [:inline " "] :m/id]]] :users]]
       :from [[:rota :r]]
       :join [[:mention :m] [:= :m.rota_id :r.id]]
       :where [:and
               [:= :r/channel channel]
               [:= :r/name rotation]]
       :group-by [:created_on :r/description]}
      sql-params))))

(defn rota-get
  [conn channel rotation]
  (into
   []
   (jdbc/execute!
    conn
    (h/format
     {:select [:m/id
               :m/rota_id
               [:m/name :user]
               :m/duty]
      :from [[:rota :r]]
      :join [[:mention :m] [:= :m.rota_id :r.id]]
      :where [:and
              [:= :r/channel channel]
              [:= :r/name rotation]]
      :order-by [[:m/id :asc]]
      :for [:update]}
     sql-params))))

(defn rota-delete!
  [channel rotation]
  (jdbc/with-transaction [conn db]
    (sql/delete! conn :rota ["channel = ? AND name = ?" channel rotation])))

(defn rota-insert!
  [params]
  (jdbc/with-transaction [conn db]
    (try
      (log/debug "Nested transactions" next.jdbc.transaction/*nested-tx*)
      (let [rota-inserted (sql/insert! conn :rota (:rota params))
            _ (log/info "Inserted rota" rota-inserted)
            rota-id (get rota-inserted :rota/id)
            mention-params (map #(assoc % :rota_id rota-id) (:mention params))
            mention-inserted (sql/insert-multi! conn :mention mention-params)
            _ (log/info "Inserted mentions" mention-inserted)]
        {:ok true})
      (catch PSQLException e
        (let [message (.getMessage e)
              duplicate? (string/includes? (.toLowerCase message) "duplicate key")
              reason (cond duplicate? :duplicate :else :other)
              error {:error {:reason reason :message message}}
              _ (.rollback conn)
              _ (log/error "Error on rota insert" error)]
          error)))))

(defn rota-update!
  [params]
  (jdbc/with-transaction [conn db]
    (try
      (let
       [rota
        (jdbc/execute!
         conn
         (h/format
          {:select [[:r/id]]
           :from [[:rota :r]]
           :where [:and
                   [:= :r/channel (get-in params [:rota :channel])]
                   [:= :r/name (get-in params [:rota :name])]]
           :for [:update]}
          sql-params))
        rota-id (-> rota first :rota/id)
        mention-params (map #(assoc % :rota_id rota-id) (:mention params))]
        (when (not rota-id)
          (throw
           (ex-info
            (format
             "Rotation not found: rota id %s, params %s"
             rota-id params) {})))
        (sql/update! conn :rota (:rota params) ["id = ?" rota-id])
        (sql/delete! conn :mention ["rota_id = ?" rota-id])
        (sql/insert-multi! conn :mention mention-params)
        {:ok true})
      (catch Exception e
        (log/error "Error on rota update" e)
        {:ok false :error {:message (.getMessage e)}}))))

(defn- assign
  [users next-duty]
  (if (not (some #{next-duty} users))
    :user-not-found
    (mapv #(assoc % :mention/duty (= % next-duty)) users)))

(defn rotate-users
  [users]
  (let [current-duty (first (filter #(:mention/duty %) users))]
    (if (not current-duty)
      users
      (let [current-duty-idx (.indexOf users current-duty)
            next-duty-idx (mod (+ current-duty-idx 1) (count users))]
        (assign users (nth users next-duty-idx))))))

(defn assign-user
  [users name]
  (let [next-duty (first (filter #(= (:mention/user %) name) users))]
    (assign users next-duty)))

(defn get-duty-user-name
  [users]
  (->> users
       (filter #(= (:mention/duty %) true))
       first
       :mention/user))

(defn- update-users
  [conn users ts]
  (let [rota_id (:mention/rota_id (first users))
        users-updated
        (reduce
         +
         (map
          (fn [user]
            (:next.jdbc/update-count
             (sql/update!
              conn :mention
              {:duty (:mention/duty user)}
              ["id = ?" (:mention/id user)]))) users))
        _ (log/info "Users updated" users-updated)
        rota-updated
        (when rota_id
          (sql/update!
           conn :rota
           {:updated_on ts}
           ["id = ?" rota_id]))
        _ (log/info "Rota updated on " ts)]
    users-updated))

(defn rotate-duty!
  [channel rotation ts]
  (jdbc/with-transaction [conn db]
    (let
     [users (rota-get conn channel rotation)
      users-count (count users)
      rotated (rotate-users users)
      _ (log/info "Rotated users" rotated)
      prev-duty (get-duty-user-name users)
      current-duty (get-duty-user-name rotated)
      users-updated (update-users conn rotated ts)]
      {:users-count users-count
       :users-updated users-updated
       :prev-duty prev-duty
       :current-duty current-duty})))

(defn assign!
  [channel rotation name ts]
  (jdbc/with-transaction [conn db]
    (let [users (rota-get conn channel rotation)
          assigned (assign-user users name)
          _ (log/debug "Current rota" users)
          _ (log/debug "Rota with a new duty assigned" assigned)]
      (if (not= assigned :user-not-found)
        (update-users conn assigned ts))
      assigned)))

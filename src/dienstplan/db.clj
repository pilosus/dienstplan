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
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [dienstplan.alerts :refer [alerts]]
   [dienstplan.config :refer [config]]
   [dienstplan.helpers :as helpers]
   [honey.sql :as h]
   [mount.core :as mount :refer [defstate]]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as rs]
   [next.jdbc.sql :as sql]
   [ragtime.next-jdbc :as ragtime-jdbc]
   [ragtime.repl :as ragtime-repl])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.sql PreparedStatement)
   (org.postgresql.util PGobject
                        PSQLException)))

(def pg-exception-insufficient-privilege "42501")

;; System component

(defstate db
  :start (connection/->pool HikariDataSource (:db config))
  ;; FIXME reflection warning
  :stop (-> ^HikariDataSource db .close))

;; For REPL-driven-development
;; Assuming that you run DB with `docker compose up postgres`
(comment
  (def ds (jdbc/get-datasource
           {:dbtype "postgres"
            :dbname "dienstplan"
            :user "dienstplan"
            :password "dienstplan"
            :host "localhost"
            :port 15432})))

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
   #'dienstplan.db/db
   #'dienstplan.alerts/alerts)
  {:datastore  (ragtime-jdbc/sql-database db)
   :migrations (ragtime-jdbc/load-resources "migrations")
   :reporter    (fn [_ op id]
                  (case op
                    :up   (log/infof "Applying migration %s" id)
                    :down (log/infof "Rolling back migration %s" id)
                    (log/infof "Migration op=%s id=%s" op id)))})

(defn- stop-migration-components! []
  (mount/stop
   #'dienstplan.db/db
   #'dienstplan.alerts/alerts
   #'dienstplan.config/config))

(defn migrate [_]
  (let [cfg (get-migration-config)]
    (try
      (ragtime-repl/migrate cfg)
      (log/info "DB migrations completed successfully")
      (catch PSQLException e
        (let [sqlstate (.getSQLState e)]
          (log/error
           e
           (if (= sqlstate pg-exception-insufficient-privilege)
             "DB migration failed: insufficient privileges for DDL (grant CREATE/ALTER or use a migration role)"
             "DB migration failed (PostgreSQL error)")))
        (throw e))
      (catch Exception e
        (log/error e "DB migration failed")
        (throw e))
      (finally
        (stop-migration-components!)))))

(defn rollback
  [_]
  (let [cfg (get-migration-config)]
    (try
      (ragtime-repl/rollback cfg)
      (log/info "DB rollback completed successfully")
      (catch PSQLException e
        (let [sqlstate (.getSQLState e)]
          (log/error
           e
           (if (= sqlstate pg-exception-insufficient-privilege)
             "DB rollback failed: insufficient privileges for DDL (down migrations need DROP/ALTER/etc.)"
             "DB rollback failed (PostgreSQL error)")))
        (throw e))
      (catch IllegalArgumentException e
        (log/error e "DB rollback failed (invalid rollback target)")
        (throw e))
      (catch Exception e
        (log/error e "DB rollback failed")
        (throw e))
      (finally
        (stop-migration-components!)))))

;; Business layer

(def sql-params {:checking :strict})

(defn duty-get
  "Get a map with current duty user and its rota"
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
  "Get a list of rotation in the channel"
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
  "Get a map with rota and its users"
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
  "Get users for a rota"
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
  "Delete a rota"
  [channel rotation]
  (jdbc/with-transaction [conn db]
    (sql/delete! conn :rota ["channel = ? AND name = ?" channel rotation])))

(defn rota-insert!
  "Insert a rota and its users"
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
  "Update a rota and its users"
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
  "Update :mention/duty value for a new duty user in the collection"
  [users next-duty]
  (if (not (some #{next-duty} users))
    :user-not-found
    (mapv #(assoc % :mention/duty (= % next-duty)) users)))

(defn rotate-users
  [^clojure.lang.PersistentVector users]
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
  "Return number of users updated in `mention` table"
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
        _ (when rota_id
            (sql/update!
             conn :rota
             {:updated_on ts}
             ["id = ?" rota_id]))
        _ (log/info "Rota updated on " ts)]
    users-updated))

(defn rotate-duty!
  "Move current duty to the next one in the `mention` table"
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
  "Set a given user mention to current and the only duty in the `mention` table"
  [channel rotation name ts]
  (jdbc/with-transaction [conn db]
    (let [users (rota-get conn channel rotation)
          assigned (assign-user users name)
          _ (log/info "Rota with a new duty assigned" assigned)]
      (when (not= assigned :user-not-found)
        (update-users conn assigned ts))
      assigned)))

(defn schedules-get
  "Get scheduled events that are ready to be executed"
  [conn now]
  (jdbc/execute!
   conn
   (h/format
    {:select [[:schedule/id]
              [:schedule/channel]
              [:schedule/executable]
              [:schedule/crontab]
              [:schedule/run_at]]
     :from [[:schedule]]
     :where [[:<= :schedule/run_at now]]
     :for [:update]}
    sql-params)))

(defn schedule-update!
  [conn params]
  (let [updated (:next.jdbc/update-count
                 (sql/update!
                  conn
                  :schedule
                  {:run_at (:schedule/run_at params)}
                  ["id = ?" (:schedule/id params)]))]
    (log/debugf "Schedule row updated: %s with params: %s" updated params)))

(defn schedule-insert!
  [params]
  (jdbc/with-transaction [^java.sql.Connection conn db]
    (try (let [inserted (sql/insert! conn :schedule params)
               explain (-> params
                           :crontab
                           helpers/cron->text)]
           (log/debugf "Schedule inserted: %s" inserted)
           {:result (when (-> inserted :schedule/id int?)
                      (format "Executable `%s` successfully scheduled with `%s` (%s)"
                              (:executable params)
                              (:crontab params)
                              explain))})
         (catch PSQLException e
           (let [message (.getMessage e)
                 duplicate? (string/includes? (.toLowerCase message) "duplicate key")
                 reason (if duplicate? :duplicate :other)
                 message-fmt (if duplicate?
                               (format "Duplicate schedule for `%s` in the channel"
                                       (:executable params))
                               message)]
             (.rollback conn)
             (log/error message-fmt)
             {:error {:reason reason :message message-fmt}}))
         (catch Exception e
           (.rollback conn)
           (log/error "Error on schedule insert" e)
           {:error {:reason :other :message (.getMessage e)}}))))

(defn schedule-delete!
  [params]
  (jdbc/with-transaction [conn db]
    (try
      (let [deleted (sql/delete!
                     conn :schedule
                     {:channel (:channel params)
                      :executable (:executable params)})
            ok? (= (:next.jdbc/update-count deleted) 1)]
        {:result (when ok?
                   (format "Scheduling for `%s` successfully deleted"
                           (:executable params)))
         :error (when (not ok?)
                  {:reason :not-found
                   :message (format "Schedule not found for params: %s" params)})})
      (catch Exception e
        (.rollback conn)
        (log/error "Error on schedule delete" e)
        {:error {:reason :other :message (.getMessage e)}}))))

(defn schedule-list
  [params]
  (jdbc/with-transaction [conn db]
    (try
      (let [schedules
            (jdbc/execute!
             conn
             (h/format
              {:select [[:schedule/executable]
                        [:schedule/crontab]]
               :from [[:schedule]]
               :where [[:= :schedule/channel (:channel params)]]
               :order-by [[:schedule/executable :asc]]
               :limit 500}
              sql-params))
            result
            (->>
             schedules
             (map #(format
                    "- `%s` with `%s`"
                    (:schedule/executable %)
                    (:schedule/crontab %)))
             (string/join \newline)
             helpers/nilify)]
        {:result (or result "No schedules found in the channel")})
      (catch Exception e
        (.rollback conn)
        (log/error "Error on listing schedules" e)
        {:error {:reason :other :message (.getMessage e)}}))))

;; Copyright (c) 2021-2022 Vitaly Samigullin and contributors. All rights reserved.
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
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as string]
   [dienstplan.config :refer [config]]
   [hikari-cp.core :as cp]
   [honey.sql :as sql]
   [mount.core :as mount :refer [defstate]]
   [ragtime.jdbc :as ragtime-jdbc]
   [ragtime.repl :as ragtime-repl])
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
  (mount/start
   #'dienstplan.config/config
   #'dienstplan.db/db)
  {:datastore  (ragtime-jdbc/sql-database db)
   :migrations (ragtime-jdbc/load-resources "migrations")})

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

(def sql-params {:checking :strict})

(defn duty-get
  [channel rotation]
  (jdbc/query
   db
   (sql/format
    {:select [[:r/id :rota_id] :r/description [:m/name :duty]]
     :from [[:rota :r]]
     :join [[:mention :m] [:= :m.rota_id :r.id]]
     :where [:and
             [:is :m/duty true]
             [:= :r/channel channel]
             [:= :r/name rotation]]}
    sql-params)))

(defn rota-list-get
  [channel]
  (jdbc/query
   db
   ["SELECT
         r.name,
         DATE(r.created_on) AS created_on
       FROM rota AS r
       WHERE
         1 = 1
         AND r.channel = ?
       ORDER BY r.created_on DESC
       LIMIT 500"
    channel]))

(defn rota-about-get
  [channel rotation]
  (jdbc/query
   db
   ["SELECT
       DATE(r.created_on) AS created_on,
       r.description,
       STRING_AGG(m.name, ' ' ORDER BY m.id ASC) AS users
     FROM rota AS r
     JOIN mention AS m ON m.rota_id = r.id
     WHERE
       1 = 1
       AND r.channel = ?
       AND r.name = ?
     GROUP BY created_on, r.description"
    channel rotation]))

(defn rota-get [conn channel rotation]
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
            ORDER BY m.id ASC
            FOR UPDATE"
     channel rotation])))

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

(defn- assign
  [users next-duty]
  (if (not (some #{next-duty} users))
    :user-not-found
    (mapv #(assoc % :duty (= % next-duty)) users)))

(defn rotate-users
  [users]
  (let [current-duty (first (filter #(:duty %) users))]
    (if (not current-duty)
      users
      (let [current-duty-idx (.indexOf users current-duty)
            next-duty-idx (mod (+ current-duty-idx 1) (count users))]
        (assign users (nth users next-duty-idx))))))

(defn assign-user
  [users name]
  (let [next-duty (first (filter #(= (:user %) name) users))]
    (assign users next-duty)))

(defn get-duty-user-name
  [users]
  (->> users
       (filter #(= (:duty %) true))
       first
       :user))

(defn- update-users
  [conn users ts]
  (let [rota_id (first users)
        users-updated
        (reduce
         +
         (map
          (fn [user]
            (first
             (jdbc/update!
              conn :mention
              {:duty (:duty user)}
              ["id = ?" (:id user)])))
          users))
        _
        (when rota_id
          (jdbc/update!
           conn :rota
           {:updated_on ts}
           ["id = ?" (:rota_id rota_id)]))]
    users-updated))

(defn rotate-duty!
  [channel rotation ts]
  (jdbc/with-db-transaction [conn db]
    (let
     [users (rota-get conn channel rotation)
      users-count (count users)
      rotated (rotate-users users)
      prev-duty (get-duty-user-name users)
      current-duty (get-duty-user-name rotated)
      users-updated (update-users conn rotated ts)]
      {:users-count users-count
       :users-updated users-updated
       :prev-duty prev-duty
       :current-duty current-duty})))

(defn assign!
  [channel rotation name ts]
  (jdbc/with-db-transaction
    [conn db]
    (let
     [users (rota-get conn channel rotation)
      assigned (assign-user users name)]
      (if (not= assigned :user-not-found)
        (update-users conn assigned ts))
      assigned)))

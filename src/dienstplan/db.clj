(ns dienstplan.db
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [hikari-cp.core :as cp]
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
  (try
    (jdbc/insert! db :rota params)
    (catch PSQLException e
      (do
        (log/error e)
        {:error {:reason :duplicate :message (.getMessage e)}}))))

(ns dienstplan.db
  (:gen-class)
  (:require
   [dienstplan.config :refer [config]]
   [hikari-cp.core :as cp]
   [mount.core :as mount :refer [defstate]]
   [ragtime.jdbc :as ragtime-jdbc]
   [ragtime.repl :as ragtime-repl]))

(defstate db
  :start
  (let [db-opts (:db config)
        datasource (cp/make-datasource db-opts)]
    {:datasource datasource})
  :stop
  (-> db :datasource cp/close-datasource))

(defn get-migration-config []
  (do
    (mount/start)
    {:datastore  (ragtime-jdbc/sql-database db)
     :migrations (ragtime-jdbc/load-resources "migrations")}))

(defn migrate []
  (ragtime-repl/migrate (get-migration-config)))

(defn rollback []
  (ragtime-repl/rollback (get-migration-config)))

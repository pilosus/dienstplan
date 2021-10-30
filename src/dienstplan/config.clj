(ns dienstplan.config
  (:gen-class)
  (:require
   [mount.core :as mount :refer [defstate]]
   [clojure.tools.logging :as log]
   [yummy.config :refer [load-config]]
   [dienstplan.spec :as spec]))

(def CONFIG_PATH "resources/dienstplan/config.yaml")

(defn die-fn-repl
  "Load config die function to call in dev/repl mode upon failure"
  [e msg]
  (binding [*out* *err*]
    (println msg (ex-message e))))

(defn die-fn-prod
  "Load config die function to call in production upon failure"
  [e msg]
  (log/error e "Config error" msg)
  (System/exit 1))

(defstate config
  "Configuration map"
  :start
  (load-config {:path CONFIG_PATH
                :spec ::spec/application-config
                :die-fn die-fn-prod}))

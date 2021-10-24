(ns dienstplan.config
  (:gen-class)
  (:require
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

;; FIXME use in mount
(load-config {:path CONFIG_PATH
              :spec ::spec/application-config
              ;; FIXME change for prod once debugged
              :die-fn die-fn-repl})

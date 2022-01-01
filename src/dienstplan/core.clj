(ns dienstplan.core
  (:gen-class)
  (:require
   [bidi.bidi :as bidi]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [dienstplan.config :refer [config]]
   [dienstplan.db :as db]
   [dienstplan.endpoints :as endpoints]
   [dienstplan.logging :as logging]
   [dienstplan.middlewares :as middlewares]
   [dienstplan.spec :as spec]
   [mount.core :as mount :refer [defstate]]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]
   [sentry-clj.core :as sentry]
))

(defn wrap-handler
  [handler]
  (fn [request]
    (let [{:keys [uri]} request
          request* (bidi/match-route* endpoints/routes uri request)]
      (handler request*))))

(def app-raw (wrap-handler endpoints/multi-handler))

(def wrap-params+ (comp wrap-keyword-params wrap-params))

(def app
  (-> app-raw
      middlewares/wrap-headers-kw
      wrap-params+
      wrap-json-params
      wrap-session
      wrap-cookies
      middlewares/wrap-exception-validation
      middlewares/wrap-exception-fallback
      middlewares/wrap-request-id
      middlewares/wrap-access-log
      wrap-json-response
      middlewares/wrap-raw-body))

;; System config

(defstate logs
  :start (logging/override-logging))

(defstate alerts
  :start
  (let [dsn (get-in config [:alerts :sentry])
        debug (s/conform ::spec/->bool (get-in config [:application :debug]))
        env (get-in config [:application :env])
        app-name (get-in config [:application :name])
        version (get-in config [:application :version])
        release (format "%s:%s" app-name version)]
    (sentry/init! dsn {:environment env :debug debug :release release}))
  :stop (sentry/close!))

(defstate server
  :start
  (let [port (s/conform ::spec/->int (get-in config [:server :port]))]
    (run-jetty app {:port port :join? true}))
  :stop (.stop server))

;; CLI opts

(def run-modes #{:server :migrate :rollback})

(def cli-options
  [["-m"
    "--mode MODE"
    "Run app in the mode specified"
    :default :server
    :parse-fn #(keyword (.toLowerCase %))
    :validate [#(contains? run-modes %) (format "App run modes: %s" run-modes)]]
   ["-h" "--help" "Print this help message"]])

(defn usage
  [options-summary]
  (->> ["dienstplan is a slack bot for duty rotations"
        ""
        "Usage: diesntplan [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn validate-args
  [args]
  (let [{:keys [options _ errors summary]} (parse-opts args cli-options)
        {:keys [help mode]} options]
    (cond
      help {:exit-message (usage summary) :ok? true}
      errors {:exit-message (error-msg errors)}
      mode {:mode mode}
      :else {:exit-message (usage summary) :ok? false})))

;; Entrypoint

(defn -main
  [& args]
  (let [{:keys [mode exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case mode
        :server (mount/start)
        :migrate (db/migrate)
        :rollback (db/rollback)))))

(ns dienstplan.core
  (:gen-class)
  (:require
   [bidi.bidi :as bidi]
   [mount.core :as mount :refer [defstate]]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.middleware.session :refer [wrap-session]]
   [sentry-clj.core :as sentry]
   [dienstplan.endpoints :as endpoints]
   [dienstplan.spec :as spec]
   [dienstplan.config :refer [config]]
   [dienstplan.logging :as logging]
   [dienstplan.middlewares :as middlewares]
   [clojure.spec.alpha :as s]

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
        release (str app-name ":" version)]
    (sentry/init! dsn {:environment env :debug debug :release release}))
  :stop (sentry/close!))

(defstate server
  :start
  (let [port (s/conform ::spec/->int (get-in config [:server :port]))]
    (run-jetty app {:port port :join? true}))
  :stop (.stop server))

(defn -main
  [& args]
  (mount/start))

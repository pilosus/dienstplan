(ns dienstplan.core
  (:gen-class)
  (:require
   [bidi.bidi :as bidi]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.middleware.session :refer [wrap-session]]
   [sentry-clj.core :as sentry]
   [dienstplan.endpoints :as endpoints]
   [dienstplan.middlewares :as middlewares]))

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
      middlewares/wrap-request-id
      middlewares/wrap-exception-validation
      middlewares/wrap-exception-fallback
      wrap-json-response))

;; Entrypoint

;; FIXME move to system config
(sentry/init! "")

;; TODO access logging

(defn -main
  [& args]
  (run-jetty app {:port 8080 :join? true}))

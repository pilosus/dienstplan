(ns dienstplan.core
  (:gen-class)
  (:require
   [bidi.bidi :as bidi]
   [ring.adapter.jetty :refer [run-jetty]]
   [dienstplan.endpoints :as endpoints]))

(defn wrap-handler
  [handler]
  (fn [request]
    (let [{:keys [uri]} request
          request* (bidi/match-route* endpoints/routes uri request)]
      (handler request*))))

(def app (wrap-handler endpoints/multi-handler))

;; Entrypoint

(defn -main
  [& args]
  (run-jetty app {:port 8080 :join? true}))

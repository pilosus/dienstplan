(ns dienstplan.endpoints
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]))

(def routes
  ["/api/"
   {"healthcheck" :healthcheck
    "command" :command
    "exc" :error
    true :not-found}])

(defmulti multi-handler
  :handler)

;; Endpoints

(defmethod multi-handler :healthcheck
  [request]
  {:status 200
   :body {:status "ok"}})

(defmethod multi-handler :not-found
  [request]
  {:status 404
   :body {:message "Page not found"}})

;; FIXME for alerts testing purposes only, remove after the testing
(defmethod multi-handler :error
  [_]
  (do
    (log/error "Oooops!")
    (/ 1 0)))


(defmethod multi-handler :command
  [request]
  (let [body {:response_type "in_channel"
              :text "The bot is under construction"}
        response {:status 200 :body body}]
    response))

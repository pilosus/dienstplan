(ns dienstplan.endpoints
  (:gen-class)
  (:require
   [dienstplan.config :refer [config]]
   [dienstplan.verify :as verify]
   [clojure.tools.logging :as log]))

(def routes
  ["/api/"
   {"healthcheck" :healthcheck
    "command" {:post :command}
    "events" {:post :events}
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
  (let
      [{:keys [params]} request
       l (log/info (str (type params) params))
       body {:response_type "in_channel"
             :text "The bot is under construction"}
       response {:status 200 :body body}]
    response))


(defmethod multi-handler :events
  [request]
  (let
      [sign-key (get-in config [:slack :sign])
       {:keys [params]} request
       challenge (get params :challenge)
       l (log/info request)
       verified? (verify/request-trusted? request sign-key)
       body (if challenge
              {:challenge challenge}
              {:response_type "in_channel"
               :text "The bot is listening to your events"})
       response {:status 200 :body body}]
    response))

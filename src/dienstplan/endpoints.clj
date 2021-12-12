(ns dienstplan.endpoints
  (:gen-class)
  (:require
   [dienstplan.config :refer [config]]
   [dienstplan.verify :as verify]
   [clojure.tools.logging :as log]))

(def routes
  ["/api/"
   {"healthcheck" :healthcheck
    "events" {:post :events}
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

(defmethod multi-handler :events
  [request]
  (let
      [sign-key (get-in config [:slack :sign])
       challenge (get-in request [:params :challenge])
       _ (log/info request)
       verified? (verify/request-verified? request sign-key)
       response
       (cond
         (not verified?) {:status 403 :body {:error "Forbidden"}}
         challenge {:status 200 :body {:challenge challenge}}
         :else
         {:status 200
          :body {:response_type "in_channel"
                 :text "The bot is listening to your events"}})]
    response))

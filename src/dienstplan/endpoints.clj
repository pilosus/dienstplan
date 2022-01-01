(ns dienstplan.endpoints
  (:gen-class)
  (:require
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [dienstplan.commands :as cmd]
   [dienstplan.config :refer [config]]
   [dienstplan.spec :as spec]
   [dienstplan.verify :as verify]
))

(def routes
  ["/api/"
   {"healthcheck" :healthcheck
    "events" {:post :events}
    true :not-found}])

(defmulti multi-handler
  :handler)

;; Endpoints

(defmethod multi-handler :healthcheck
  [_]
  {:status 200
   :body {:status "ok"}})

(defmethod multi-handler :not-found
  [_]
  {:status 404
   :body {:message "Page not found"}})

(defmethod multi-handler :events
  [request]
  (let
      [debug (s/conform ::spec/->bool (get-in config [:application :debug]))
       sign-key (get-in config [:slack :sign])
       challenge (get-in request [:params :challenge])
       _ (log/info request)
       verified? (or debug (verify/request-verified? request sign-key))
       response
       (cond
         (not verified?) {:status 403 :body {:error "Forbidden"}}
         challenge {:status 200 :body {:challenge challenge}}
         :else {:status 200 :body (cmd/send-command-response request)})]
    response))

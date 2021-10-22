(ns dienstplan.endpoints
  (:gen-class))

(def routes
  ["/api/"
   {"healthcheck" :healthcheck
    true :not-found}])

(defmulti multi-handler
  :handler)

;; Endpoints

(defmethod multi-handler :healthcheck
  [request]
  {:status 200
   :headers {"content-type" "text-plain"}
   :body "ok"})

(defmethod multi-handler :not-found
  [request]
  {:status 404
   :headers {"content-type" "text-plain"}
   :body "Page not found"})

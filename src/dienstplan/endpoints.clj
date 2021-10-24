(ns dienstplan.endpoints
  (:gen-class))

(def routes
  ["/api/"
   {"healthcheck" :healthcheck
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
  (/ 1 0))

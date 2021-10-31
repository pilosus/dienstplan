(ns dienstplan.middlewares
  (:gen-class)
  (:require
   [clojure.walk :refer [keywordize-keys stringify-keys]]
   [clojure.tools.logging :as log]
   [sentry-clj.core :as sentry])
  (:import (java.util UUID)))

;; Middlewares

(defn wrap-headers-kw
  [handler]
  (fn [request]
    (-> request
        (update :headers keywordize-keys)
        handler
        (update :headers stringify-keys))))

(defn wrap-request-id
  [handler]
  (fn [request]
    (let [uuid (or (get-in request [:headers :x-request-id])
                   (str (UUID/randomUUID)))]
      (-> request
          (assoc-in [:headers :x-request-id] uuid)
          (assoc :request-id uuid)
          handler
          (assoc :request-id uuid)
          (assoc-in [:headers "x-request-id"] uuid)))))

(defn wrap-exception-validation
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (let [error-type (-> e ex-data :cause)
              messages (-> e ex-data :messages)]
          (if (= error-type :validation)
            {:status 400 :body {:errors messages}}
            (throw e)))))))

;; Unhandled error tracking

(defn get-sentry-context
  "Create a context map for Sentry event"
  [request]
  (let [{:keys [uri query-string request-method remote-addr headers]} request]
    {:request {:url uri
               :method (name request-method)
               :query-string (str query-string)
               :headers headers}
     :user {:ip-address remote-addr}}))

(defn wrap-exception-fallback
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (try
          (let [sentry-context (get-sentry-context request)
                sentry-error {:message "Something has gone wrong!" :throwable e}
                sentry-event (merge sentry-error sentry-context)]
            (log/error e "Request failed")
            (sentry/send-event sentry-event)
            ;; For some reason cannot move the response to finally,
            ;; the block executed, but never returned from the endpoint
            {:status 500
             :body {:error "Internal error"}})
          (catch Exception sentry-e
            (log/error sentry-e "Sentry error")
            {:status 500
             :body {:error "An error occured while reporting another error"}}))))))

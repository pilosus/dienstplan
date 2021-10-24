(ns dienstplan.middlewares
  (:gen-class)
  (:require
   [clojure.walk :refer [keywordize-keys stringify-keys]]
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
            {:status 400 :body {:errors messages}}))))))

(defn wrap-exception-fallback
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (sentry/send-event {:message "Something has gone wrong!"
                            :throwable e})
        {:status 500 :body {:error (str e)}}))))

;; Copyright (c) Vitaly Samigullin and contributors. All rights reserved.
;;
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0.
;;
;; This Source Code may also be made available under the following Secondary
;; Licenses when the conditions for such availability set forth in the Eclipse
;; Public License, v. 2.0 are satisfied: GNU General Public License as published by
;; the Free Software Foundation, either version 2 of the License, or (at your
;; option) any later version, with the GNU Classpath Exception which is available
;; at https://www.gnu.org/software/classpath/license.html.
;;
;; SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

(ns dienstplan.middlewares
  (:gen-class)
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [clojure.walk :refer [keywordize-keys stringify-keys]]
   [dienstplan.config :refer [config]]
   [sentry-clj.core :as sentry])
  (:import (java.util UUID)))

;; Middlewares

(defn string->stream
  "Convert string to InputStream"
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(defn wrap-raw-body
  "Save original request body"
  [handler]
  (fn [request]
    (let [raw-body (slurp (:body request))
          request' (-> request
                       (assoc :raw-body raw-body)
                       (assoc :body (string->stream raw-body)))]
      (handler request'))))

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

;; Access logs

(def loglevel-str-to-kw
  {"DEBUG" :debug
   "INFO" :info
   "WARN" :warn
   "ERROR" :error
   "FATAL" :fatal})

(defn wrap-access-log
  [handler]
  (fn [request]
    (let [enable-logging? (get-in config [:server :access-log])
          {:keys [query-string request-method uri]} request
          loglevel-str (get-in config [:server :loglevel])
          loglevel-kw (get loglevel-str-to-kw loglevel-str)
          method (-> request-method name string/upper-case)
          query-params (if query-string (str "?" query-string) "")
          message (format "%s %s%s" method uri query-params)]
      (when enable-logging?
        (log/log loglevel-kw message))
      (handler request))))

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
            (log/error e (format "Request failed: %s with error: %s" request e))
            (sentry/send-event sentry-event)
            ;; For some reason cannot move the response to finally,
            ;; the block executed, but never returned from the endpoint
            {:status 500
             :body {:error "Internal error"}})
          (catch Exception sentry-e
            (log/error sentry-e "Sentry error")
            {:status 500
             :body {:error "An error occured while reporting another error"}}))))))

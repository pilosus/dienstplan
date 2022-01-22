;; Copyright (c) 2022 Vitaly Samigullin and contributors. All rights reserved.
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

(ns dienstplan.slack
  "Slack public API requests"
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.spec.alpha :as s]
   [clj-http.client :as http]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [dienstplan.spec :as spec]))

;; Const

(def HTTP-BASE-PARAMS
  {:accept :json
   :max-redirects 1
   :socket-timeout 1000
   :connection-timeout 1000})

(def AUTH-TOKEN
  (format "Bearer %s" (get-in config [:slack :token])))

;; Helpers

(defn get-headers
  ([] {"Authorization" AUTH-TOKEN})
  ([headers] (merge (get-headers) headers)))

(defn get-params
  ([] HTTP-BASE-PARAMS)
  ([params] (merge (get-params) params)))

(s/fdef parse-http-response
  :args (s/coll-of ::spec/http-raw-response)
  :ret ::spec/http-parsed-response)

(defn parse-http-response
  [response-raw]
  (let [body (:body response-raw)
        status (:status response-raw)
        data (json/parse-string body)
        ok? (or (get data "ok") false)
        response-parsed {:status status :ok? ok? :data data}]
    response-parsed))

(defn http-request [method url & [params]]
  (let [response-raw
        (try
          (http/request
           (merge {:method method :url url} params))
          (catch Exception exc
            (let [data (ex-data exc)
                  status (:status data)
                  method-str (string/upper-case (name method))
                  _ (log/error
                     (format "Request %s %s failed: %s" method-str url data))
                  result {:status status :ok? false :data nil}]
              result)))
        response-parsed (parse-http-response response-raw)]
    response-parsed))

;; Slack API methods

(s/fdef slack-api-request
  :args (s/coll-of ::spec/slack-api-request)
  :ret ::spec/http-parsed-response)

(defmulti slack-api-request
  "Call Slack API method"
  (fn [slack-request] (get slack-request :method)))

(defmethod slack-api-request
  :users.info
  [slack-request]
  (let [{:keys [query-params]} slack-request
        url "https://slack.com/api/users.info"
        headers (get-headers)
        base-params (get-params {:query-params query-params})
        params (merge base-params {:headers headers})
        response (http-request :get url params)]
    response))

(defmethod slack-api-request
  :chat.postMessage
  [slack-request]
  (let [{:keys [body]} slack-request
        url "https://slack.com/api/chat.postMessage"
        headers (get-headers {"Content-type" "application/json; charset=utf-8"})
        base-params (get-params {:body body})
        params (merge base-params {:headers headers})
        response (http-request :post url params)]
    response))

(defmethod slack-api-request
  :default [_]
  {:ok? false :status 500 :data nil})

;; Business layer

(defn get-user-real-name
  [mention]
  (let [mention-str (or mention "malformed-user-id")
        user-id (string/replace mention-str #"\<@([A-Z0-9]+)\>" "$1")
        {:keys [data]}
        (slack-api-request
         {:method :users.info
          :query-params {"user" user-id}})
        real-name (get-in data ["user" "real_name"])
        result (if (string/blank? real-name) mention-str real-name)]
    result))

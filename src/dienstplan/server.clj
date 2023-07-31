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

(ns dienstplan.server
  (:require
   [bidi.bidi :as bidi]
   [dienstplan.alerts :refer [alerts]]
   [dienstplan.config :refer [config]]
   [dienstplan.db :as db]
   [dienstplan.endpoints :as endpoints]
   [dienstplan.logging :refer [logs]]
   [dienstplan.middlewares :as middlewares]
   [mount.core :as mount :refer [defstate]]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]))

(defn wrap-handler
  [handler]
  (fn [request]
    (let [{:keys [uri] :or {uri "/"}} request
          request* (bidi/match-route* endpoints/routes uri request)]
      (handler request*))))

(def app-raw (wrap-handler endpoints/multi-handler))

(def wrap-params+ (comp wrap-keyword-params wrap-params))

(def app
  (-> app-raw
      middlewares/wrap-headers-kw
      wrap-params+
      wrap-json-params
      wrap-session
      wrap-cookies
      middlewares/wrap-exception-validation
      middlewares/wrap-exception-fallback
      middlewares/wrap-request-id
      middlewares/wrap-access-log
      wrap-json-response
      middlewares/wrap-raw-body))

(defstate server
  :start
  (let [port (get-in config [:server :port])
        join? (get-in config [:server :block-thread])]
    (run-jetty app {:port port :join? join?}))
  :stop (.stop server))

;; Entrypoint

(defn run
  "Run Jetty server"
  [_]
  (mount/start))

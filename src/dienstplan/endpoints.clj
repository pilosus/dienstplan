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

(ns dienstplan.endpoints
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]
   [dienstplan.commands :as cmd]
   [dienstplan.config :refer [config]]
   [dienstplan.verify :as verify]))

(def routes
  ["/" {"api/" {"healthcheck" :healthcheck
                "events" {:post :events}}
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
   [debug (get-in config [:application :debug])
    sign-key (get-in config [:slack :sign])
    challenge (get-in request [:params :challenge])
    _ (log/info request)
    verified? (or debug (verify/request-verified? request sign-key))
    response
    (cond
      (not verified?) {:status 403 :body {:error "Forbidden"}}
      challenge {:status 200 :body {:challenge challenge}}
      :else {:status 200 :body (cmd/send-command-response! request)})]
    response))

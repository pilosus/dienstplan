;; Copyright (c) 2021-2022 Vitaly Samigullin and contributors. All rights reserved.
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

(ns dienstplan.endpoints-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.endpoints :as e]
   [dienstplan.verify :as verify]
   [dienstplan.commands :as cmd]
   [dienstplan.config :as cfg]))

(def params-multi-handler-other
  [[{:handler :healthcheck}
    {:status 200
     :body {:status "ok"}}
    "healthcheck"]
   [{:handler :not-found}
    {:status 404
     :body {:message "Page not found"}}
    "page not found"]])

(deftest test-multi-handler-other
  (testing "Test multi-handler other"
    (doseq [[request expected description] params-multi-handler-other]
      (testing description
        (is (= expected (e/multi-handler request)))))))

(def params-multi-handler-events
  [[{:handler :events
     :params {:challenge "challenge"}}
    {:application {:debug "true"}}
    true
    nil
    {:status 200 :body {:challenge "challenge"}}
    "Challenge check with debug mode"]
   [{:handler :events
     :params {:challenge "challenge"}}
    {:application {:debug "false"}}
    true
    nil
    {:status 200 :body {:challenge "challenge"}}
    "Challenge check with successfull verification"]
   [{:handler :events
     :params {:challenge "challenge"}}
    {:application {:debug "false"}}
    false
    nil
    {:status 403 :body {:error "Forbidden"}}
    "Challenge check with failed verification"]
   [{:handler :events}
    {:application {:debug "false"}}
    true
    {:text "ok" :channel "C123"}
    {:status 200 :body {:text "ok" :channel "C123"}}
    "Normal command response"]])

(deftest test-multi-handler-events
  (testing "Test multi-handler events"
    (doseq [[request
             app-config
             verified?
             command-response
             expected
             description] params-multi-handler-events]
      (testing description
        (with-redefs [verify/request-verified? (constantly verified?)
                      cfg/config (constantly app-config)
                      cmd/send-command-response! (constantly command-response)]
          (is (= expected (e/multi-handler request))))))))

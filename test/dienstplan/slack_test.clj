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

(ns dienstplan.slack-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.spec.test.alpha :refer [instrument]]
   [dienstplan.slack :as slack]
   [clj-http.client :as http]))

(instrument `slack/parse-http-response)
(instrument `slack/slack-api-request)

(def params-get-user-name
  [["<@U123>" {:data {"user" {"real_name" "user1"}}} "user1" "Real name found"]
   ["<@U123>" {:data {"user" {"real_name" ""}}} "<@U123>" "Real name is empty string"]
   ["<@U123>" {:data {"user" {"real_name" nil}}} "<@U123>" "Real name is nil"]
   ["<@U123>" {:data {"user" {"something" "test"}}} "<@U123>" "Real name is absent"]
   ["malformed" {:data {"user" {"something" "test"}}} "malformed" "Use mention as it is"]
   [nil {:data {"user" {"something" "test"}}} "malformed-user-id" "Mention is nil"]])

(deftest test-get-user-name
  (testing "Get user name"
    (doseq [[mention slack-response expected description] params-get-user-name]
      (testing description
        (with-redefs [slack/slack-api-request (constantly slack-response)]
          (is (= expected (slack/get-user-name mention))))))))

(def params-slack-api-request
  [[{:method :no-such-method}
    (constantly {:status 200 :body "{}"})
    {:ok? false :status 500 :data nil}
    "No such method"]
   [{:method :users.info :query-params {"user" "U123"}}
    (constantly {:status 200 :body "{\"ok\": true, \"user\": {\"real_name\": \"me\"}}"})
    {:ok? true :status 200 :data {"ok" true, "user" {"real_name" "me"}}}
    "users.info ok"]
   [{:method :chat.postMessage :body {"text" "something"}}
    (constantly {:status 201 :body "{}"})
    {:ok? false :status 201 :data {}}
    "chat.postMessage not ok"]
   [{:method :chat.postMessage :body {"text" "something"}}
    (fn [& _]
      (throw (ex-info "Boom!" {:status 500 :body "<html><title>Boom!</title></html>"})))
    {:ok? false :status 500 :data nil}
    "Exception"]])

(deftest test-slack-api-request
  (testing "Slack API request"
    (doseq [[slack-request http-response expected description] params-slack-api-request]
      (testing description
        (with-redefs [http/request http-response]
          (is (= expected (slack/slack-api-request slack-request))))))))

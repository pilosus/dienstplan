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

(ns dienstplan.commands-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [clojure.spec.test.alpha :refer [instrument]]
   [dienstplan.commands :as cmd]
   [dienstplan.db :as db]
   [dienstplan.slack :as slack]
   [clj-http.client :as http]))

;; Instrumenting functions with specs

(instrument `cmd/get-request-context-with-text)
(instrument `cmd/parse-command)
(instrument `cmd/get-command-args)
(instrument `cmd/parse-args)
(instrument `cmd/get-command-map)
(instrument `cmd/get-command-response)
(instrument `cmd/send-command-response!)
(instrument `cmd/command-exec!)

;; Tests

(def params-parse-command
  [["this is a text" nil "No command found"]
   ["<@U02HXENLLPN> create backend-rota <@U1KF3FG75> <@U01NT7XLST0> <@U01P02NDVSN>\nOn-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"
    {:command :create :rest "backend-rota <@U1KF3FG75> <@U01NT7XLST0> <@U01P02NDVSN>\nOn-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"}
    "All parts parsed"]
   ["<@U001>\u00a0create backend rotation\u00a0<@U123>\u00a0<@U456>\u00a0<@U789>\u00a0On-call backend engineer's duty:\n- Check support questions\n- Check alerts\n- Check metrics"
    {:command :create :rest "backend rotation\u00a0<@U123>\u00a0<@U456>\u00a0<@U789>\u00a0On-call backend engineer's duty:\n- Check support questions\n- Check alerts\n- Check metrics"}
    "Unicode whitespaces"]
   ["  <@U123> rotate "
    {:command :rotate :rest nil}
    "No args parsed"]
   ["  <@U123> command"
    {:command nil :rest nil}
    "Command is unknown, fallback to nil"]
   ["  <@U123> " nil "No command specified"]
   [" command args" nil "No user id specified"]
   [nil nil "nil text"]])

(deftest test-parse-command
  (testing "Parse command from app mention response"
    (doseq [[text expected description] params-parse-command]
      (testing description
        (is (= expected (cmd/parse-command text)))))))

(def params-get-user-mentions
  [["single user <@U123> mentioned"
    ["<@U123>"]
    "Single user"]
   ["run command for users <@U123>, <@U345> and <@U678> please"
    ["<@U123>" "<@U345>" "<@U678>"]
    "Multiple users mentioned"]
   [nil nil "Nil"]
   ["" nil "Blank string"]])

(deftest test-get-user-mentions
  (testing "Get list of users mentioned in the string"
    (doseq [[text expected description] params-get-user-mentions]
      (testing description
        (is (= expected (cmd/parse-user-mentions text)))))))

(def params-parse-args
  [[{:user-id "U02HXENLLPN" :command :create :rest "backend-rota <@U1KF3FG75> <@U01NT7XLST0> <@U01P02NDVSN>\nOn-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"}
    {:rotation "backend-rota"
     :users ["<@U1KF3FG75>" "<@U01NT7XLST0>" "<@U01P02NDVSN>"]
     :description "On-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"}
    "Create"]
   [{:command :create :rest "backend rotation\u00a0<@U123>\u00a0<@U456>\u00a0<@U789>\u00a0On-call backend engineer's duty:\n- Check support questions\n- Check alerts\n- Check metrics"}
    {:rotation "backend rotation"
     :users ["<@U123>" "<@U456>" "<@U789>"]
     :description "On-call backend engineer's duty:\n- Check support questions\n- Check alerts\n- Check metrics"}
    "Unicode whitespaces"]
   [{:user-id "U123" :command :help :rest nil}
    {:description cmd/help-msg}
    "Help"]
   [{:user-id "U123" :command :delete :rest " backend-rota "}
    {:rotation "backend-rota"}
    "Delete"]
   [{:user-id "U123" :command :about :rest " backend-rota "}
    {:rotation "backend-rota"}
    "About"]
   [{:user-id "U123" :command :rotate :rest " backend-rota "}
    {:rotation "backend-rota"}
    "Rotate"]
   [{:user-id "U123" :command :assign :rest " backend-rota <@U456>"}
    {:rotation "backend-rota" :user "<@U456>"}
    "Assign"]
   [{:user-id "U123" :command :assign :rest "<@U456>"}
    {:rotation nil :user "<@U456>"}
    "Assign with no rota name"]
   [{:user-id "U123" :command :who :rest " backend-rota "}
    {:rotation "backend-rota"}
    "Who"]
   [{:user-id "U123" :command :something :rest " backend-rota "}
    nil
    "Unrecognized command"]])

(deftest test-parse-args
  (testing "Parse command arguments"
    (doseq [[args expected description] params-parse-args]
      (testing description
        (is (= expected (cmd/parse-args args)))))))

(def request-app-mention
  {:request-id "1469a826-8221-498d-a8ac-39621f36a9c6"
   :json-params
   {"event_id"  "test_event_id"
    "api_app_id" "test_api_app_id",
    "token" "test_token",
    "authorizations"
    [{"enterprise_id" nil,
      "team_id" "T123",
      "user_id" "U123",
      "is_bot" true,
      "is_enterprise_install" false}],
    "team_id" "T123",
    "event_context" "huge random string goes here",
    "event" {"bot_id" "B01",
             "type" "app_mention",
             "text" "<@U123> create backend-rota <@U435> <@U567> and <@U789> Do what thou wilt shall be the whole of the Law",
             "user" "USLACKBOT",
             "ts" "1640250011.000100",
             "team" "T123",
             "channel" "C123",
             "event_ts" "1640250011.000100"},
    "event_time" "1640250011",
    "type" "event_callback",
    "is_ext_shared_channel" false},
   :ssl-client-cert nil,
   :protocol " HTTP/1.0",
   :cookies {},
   :remote-addr "127.0.0.1",
   :params {:event_context "huge random string goes here",
            :api_app_id "test_api_app_id",
            :authorizations [{:enterprise_id nil,
                              :team_id "T123",
                              :user_id "U123",
                              :is_bot true,
                              :is_enterprise_install false}],
            :type "event_callback",
            :event_id "test_event_id",
            :token "test_token",
            :event {:bot_id "B01",
                    :type "app_mention",
                    :text "<@U123> create backend-rota <@U435> <@U567> and <@U789> Do what thou wilt shall be the whole of the Law",
                    :user "USLACKBOT",
                    :ts "1640250011.000100",
                    :team "T123",
                    :channel "C123",
                    :event_ts "1640250011.000100"},
            :team_id "T123T1KGQ5533",
            :is_ext_shared_channel false,
            :event_time "1640250011"},
   :headers {:user-agent "Slackbot 1.0 (+https://api.slack.com/robots)",
             :x-slack-request-timestamp "1640250012",
             :host "localhost",
             :content-length "676",
             :x-slack-signature "v0=0123456789eb7b17c6d6357370665693662af8cac7945957e1b46d884a50fcb8",
             :accept-encoding "gzip,deflate",
             :content-type "application/json",
             :x-real-ip "10.192.79.32",
             :connection "close",
             :x-forwarded-for "10.192.79.32",
             :x-request-id "1469a826-8221-498d-a8ac-39621f36a9b1",
             :accept "*/*"},
   :server-port "80",
   :content-length "676",
   :form-params {},
   :session/key nil,
   :query-params {},
   :content-type "application/json",
   :character-encoding "UTF-8",
   :uri "/api/events",
   :server-name "localhost",
   :query-string nil,
   :body nil,
   :handler :events,
   :scheme :http,
   :request-method :post,
   :raw-body "{\"event_context\":\"huge random string goes here\",\"api_app_id\":\"test_api_app_id\",\"authorizations\":[{\"enterprise_id\":null,\"team_id\":\"T123\",\"user_id\":\"U123\",\"is_bot\":true,\"is_enterprise_install\":false}],\"type\":\"event_callback\",\"event_id\":\"test_event_id\",\"token\":\"test_token\",\"event\":{\"bot_id\":\"B01\",\"type\":\"app_mention\",\"text\":\"<@U123> create backend-rota <@U435> <@U567> and <@U789> Do what thou wilt shall be the whole of the Law\",\"user\":\"USLACKBOT\",\"ts\":\"1640250011.000100\",\"team\":\"T123\",\"channel\":\"C123\",\"event_ts\":\"1640250011.000100\"},\"team_id\":\"T123T1KGQ5533\",\"is_ext_shared_channel\":false,\"event_time\":\"1640250011\"}",
   :session {}})

(def params-get-event-app-mention
  [[request-app-mention
    {:channel "C123"
     :team "T123"
     :ts "1640250011.000100"
     :text "<@U123> create backend-rota <@U435> <@U567> and <@U789> Do what thou wilt shall be the whole of the Law"}
    "real world request"]])

(deftest test-get-event-app-mention
  (testing "Get valuable params from the app_mention slack bot request"
    (doseq [[request expected description] params-get-event-app-mention]
      (testing description
        (is (= expected (cmd/get-request-context-with-text request)))))))

(def params-test-get-command
  [[request-app-mention
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :create
     :args
     {:rotation "backend-rota"
      :users ["<@U435>" "<@U567>" "<@U789>"]
      :description "Do what thou wilt shall be the whole of the Law"}}
    "Create command"]
   [{:params {:event {:text "<@U001>\u00a0create backend rotation\u00a0<@U123>\u00a0<@U456>\u00a0<@U789>\u00a0On-call backend engineer's duty:\n- Check support questions\n- Check alerts\n- Check metrics"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :create
     :args {:rotation "backend rotation"
            :users ["<@U123>" "<@U456>" "<@U789>"]
            :description "On-call backend engineer's duty:\n- Check support questions\n- Check alerts\n- Check metrics"}}
    "Unicode chars"]
   [{:params {:event {:text "<@U001> create <@U123> <@U456> test"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :create
     :args {:rotation nil
            :users ["<@U123>" "<@U456>"]
            :description "test"}
     :error cmd/help-cmd-create}
    "Create with no rotation name"]
   [{:params {:event {:text "  <@UNX01> who backend-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :who
     :args {:rotation "backend-rota"}}
    "Who command"]
   [{:params {:event {:text "<@UNX01> about backend-rota"
                      :ts "1640250011.000100"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :channel "C123"}
     :command :about
     :args {:rotation "backend-rota"}}
    "About command"]
   [{:params {:event {:text "  <@UNX01> who backend-rota"
                      :ts "1640250011.000100"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :channel "C123"}
     :command :who
     :args {:rotation "backend-rota"}}
    "Team id is optional"]
   [{:params {:event {:text "<@UNX01> list"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :list
     :args nil}
    "List command"]
   [{:params {:event {:text "  <@UNX01> unrecognized-command some args go here"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :error cmd/help-msg}
    "Unrecognized command"]
   [{:params {:event {:text "  <@UNX01> who"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :who
     :args {:rotation nil}
     :error cmd/help-cmd-who}
    "No args provided for who command"]
   [{:params {:event {:text "<@UNX01> assign my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :assign
     :args {:rotation "my-rota" :user nil}
     :error cmd/help-cmd-assign}
    "No user mentioned provided for assign command"]
   [{:params {:event {:text "broken text"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :error cmd/help-msg}
    "No groups matched with regex in the text"]
   [{:params {:event {:text "Reminder: <@U02HXENLLPN|dienstplan> rotate backend-rota."
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :rotate
     :args {:rotation "backend-rota"}}
    "Real life rotate from reminder"]
   [{:params {:event {:text "<@UNX01> assign backend-rota <@U123>"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:context
     {:ts "1640250011.000100"
      :team "T123"
      :channel "C123"}
     :command :assign
     :args {:rotation "backend-rota" :user "<@U123>"}}
    "Assign command"]])

(deftest test-get-command
  (testing "Get parsed command map"
    (doseq [[request expected description] params-test-get-command]
      (testing description
        (is (= expected (cmd/get-command-map request)))))))

(deftest test-get-command-response
  (testing "Get response to be sent to the channel"
    (doseq [[request expected description] params-test-get-command]
      (testing description
        (with-redefs [cmd/command-exec! (constantly "okay")
                      http/request (constantly {:status 200 :body "{\"ok\": true}"})]
          (let [expected' (if (:error expected)
                            {:channel (get-in expected [:context :channel])
                             :text (:error expected)}
                            {:channel (get-in expected [:context :channel])
                             :text "okay"})]
            (do
              (is (= expected'
                     (cmd/get-command-response request)))
              (is (= expected'
                     (cmd/send-command-response! request))))))))))

(def params-slack-mention-channel
  [["C123" "<#C123>" "Make channel name mentionable"]
   ["<#C456>" "<#C456>" "Do nothing"]])

(deftest test-slack-mention-channel
  (testing "Test slack channel mention formatting"
    (doseq [[text expected description] params-slack-mention-channel]
      (testing description
        (is (= expected (cmd/slack-mention-channel text)))))))

(def params-users->mention-table-rows
  [[["user1" "user2"]
    [{:name "user1" :duty true} {:name "user2" :duty false}]
    "Normal list"]
   [[] [] "Empty list"]
   [nil [] "Empty nil list"]])

(deftest test-users->mention-table-rows
  (testing "Test extending user list with duty key"
    (doseq [[users expected description] params-users->mention-table-rows]
      (testing description
        (is (= expected (cmd/users->mention-table-rows users)))))))

(def params-command-exec!-who
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :who :args {:rotation "rota"}}
    [{:duty "user1" :description "Do what thou wilt shall be the whole of the Law"}]
    "Hey user1, you are an on-call person for `rota` rotation.\nDo what thou wilt shall be the whole of the Law"
    "Rota found"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :who :args {:rotation "rota"}}
    []
    "Rotation `rota` not found in channel <#channel>"
    "Rota not found"]])

(deftest test-command-exec!-who
  (testing "Test command-exec! who"
    (doseq [[command duty expected description] params-command-exec!-who]
      (testing description
        (with-redefs [db/duty-get (constantly duty)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-about
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :about :args {:rotation "rota"}}
    [{:created_on "2021-01-01" :description "Test" :users "<U123> <U456> <U789>"}]
    "Rotation `rota` [2021-01-01] list: <U123> <U456> <U789>.\nTest"
    "Rota found"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :about :args {:rotation "non-existent"}}
    []
    "Rotation `non-existent` not found in channel <#channel>"
    "Rota not found"]])

(deftest test-command-exec!-about
  (testing "Test command-exec! about"
    (doseq [[command rotation expected description] params-command-exec!-about]
      (testing description
        (with-redefs [db/rota-about-get (constantly rotation)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-delete
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :delete :args {:rotation "rota"}}
    [1]
    "Rotation `rota` has been deleted"
    "Deleted"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :delete :args {:rotation "rota"}}
    [0]
    "Rotation `rota` not found in channel <#channel>"
    "Rotation not found"]])

(deftest test-command-exec!-delete
  (testing "Test command-exec! delete"
    (doseq [[command deleted expected description] params-command-exec!-delete]
      (testing description
        (with-redefs [db/rota-delete! (constantly deleted)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-create
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :create :args {:rotation "rota" :description "todo"}}
    {}
    "Rotation `rota` for channel <#channel> created successfully"
    "Created"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :create :args {:rotation "rota" :description "todo"}}
    {:error {:reason :duplicate}}
    "Rotation `rota` for channel <#channel> already exists"
    "Duplicate"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :create :args {:rotation "rota" :description "todo"}}
    {:error {:reason :other :message "Connection to the database closed unexpectedly"}}
    "Cannot create rotation `rota` for channel <#channel>: Connection to the database closed unexpectedly"
    "Other error"]])

(deftest test-command-exec!-create
  (testing "Test command-exec! create"
    (doseq [[command inserted expected description] params-command-exec!-create]
      (testing description
        (with-redefs [db/rota-insert! (constantly inserted)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-list
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :list}
    [{:name "rota 1" :created_on "2021-01-30"} {:name "rota 2" :created_on "2021-11-15"}]
    "Rotations created in channel <#channel>:\n- `rota 1` [2021-01-30]\n- `rota 2` [2021-11-15]"
    "Rotations found"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :list}
    []
    "No rotations found in channel <#channel>"
    "No rotations found"]])

(deftest test-command-exec!-list
  (testing "Test command-exec! list"
    (doseq [[command rota-list expected description] params-command-exec!-list]
      (testing description
        (with-redefs [db/rota-list-get (constantly rota-list)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-rotate
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :rotate :args {:rotation "rota"}}
    {:users-count 3 :users-updated 3}
    "Users in rotation `rota` of channel <#channel> have been rotated from Mr.User to Mr.User"
    "Rotated"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :rotate :args {:rotation "rota"}}
    {:users-count 3 :users-updated 0}
    "Failed to rotate users in rotation `rota` of channel <#channel>"
    "Failed to rotate users"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :rotate :args {:rotation "rota"}}
    {:users-count 0 :users-updated 0}
    "No users found in rotation `rota` of channel <#channel>"
    "Not found"]])

(deftest test-command-exec!-rotate
  (testing "Test command-exec! rotate"
    (doseq [[command rotation expected description] params-command-exec!-rotate]
      (testing description
        (with-redefs [slack/get-user-name (constantly "Mr.User")
                      db/rotate-duty! (constantly rotation)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-assign
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :assign :args {:rotation "rota" :user "<@U123>"}}
    :user-not-found
    "User <@U123> is not found in rotation `rota` of channel <#channel>"
    "User not found"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :assign :args {:rotation "rota" :user "<@U123>"}}
    [{:id 1 :user "<@U123>" :duty true}]
    "Assigned user <@U123> in rotation `rota` of channel <#channel>"
    "Assigned"]])

(deftest test-command-exec!-assign
  (testing "Test command-exec! assign"
    (doseq [[command assigned expected description] params-command-exec!-assign]
      (testing description
        (with-redefs [db/assign! (constantly assigned)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-command-exec!-default
  [[{:context {:channel "channel" :ts "1640250011.000100"} :command :help}
    (format cmd/help-intro "0.2.7" cmd/help-msg)
    "Help command"]
   [{:context {:channel "channel" :ts "1640250011.000100"} :command :whatever :args {:rotation "rota"}}
    (format cmd/help-intro "0.2.7" cmd/help-msg)
    "Whatever command"]])

(deftest test-command-exec!-default
  (testing "Test command-exec! default arg"
    (doseq [[command expected description] params-command-exec!-default]
      (testing description
        (with-redefs [cmd/get-help-message (constantly expected)]
          (is (= expected (cmd/command-exec! command))))))))

(def params-str-trim
  [[nil nil "Nil"]
   ["text" "text" "Nothing to change"]
   ["text\u00A0" "text" "Right trim"]
   ["\u2007text" "text" "Left trim"]
   ["\u2007text\u202F" "text" "Full trim"]])

(deftest test-str-trim
  (testing "Test str-trim"
    (doseq [[s expected description] params-str-trim]
      (testing description
        (is (= expected (cmd/str-trim s)))))))

(ns dienstplan.commands-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.commands :as cmd]))

(def params-parse-app-mention
  [["this is a text" nil "No command found"]
   ["<@U02HXENLLPN> create backend-rota <@U1KF3FG75> <@U01NT7XLST0> <@U01P02NDVSN>\nOn-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"
    {:user-id "U02HXENLLPN" :command :create :rest "backend-rota <@U1KF3FG75> <@U01NT7XLST0> <@U01P02NDVSN>\nOn-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"}
    "All parts parsed"]
   ["  <@U123> rotate "
    {:user-id "U123" :command :rotate :rest nil}
    "User id and command parsed"]
   ["  <@U123> command"
    {:user-id "U123" :command nil :rest nil}
    "User id and command parsed, command is unknown, fallback to nil"]
   ["  <@U123> " nil "No command specified"]
   [" command args" nil "No user id specified"]
   [nil nil "nil text"]
   ;; https://api.slack.com/changelog/2017-09-the-one-about-usernames
   ["<@U123> show my arguments"
    {:user-id "U123" :command :show :rest "my arguments"}
    "Deprecated user mentioning syntax is recognized"]])

(deftest test-parse-app-mention
  (testing "Parse app mention text response"
    (doseq [[text expected description] params-parse-app-mention]
      (testing description
        (is (= expected (cmd/parse-app-mention text)))))))

(def params-get-user-mentions
  [["single user <@U123> mentioned"
    ["U123"]
    "Single user"]
   ["run command for users <@U123>, <@U345> and <@U678> please"
    ["U123" "U345" "U678"]
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
    {:name "backend-rota"
     :users ["U1KF3FG75" "U01NT7XLST0" "U01P02NDVSN"]
     :description "On-call backend engineer's duty \n- Check <#C02PJGR5LLB>\n- Check Sentry alerts\n- Check Grafana metrics"}
    "Create"]
   [{:user-id "U123" :command :help :rest nil}
    {:description cmd/help-msg}
    "Help"]
   [{:user-id "U123" :command :delete :rest " backend-rota "}
    {:name "backend-rota"}
    "Delete"]
   [{:user-id "U123" :command :rotate :rest " backend-rota "}
    {:name "backend-rota"}
    "Rotate"]
   [{:user-id "U123" :command :show :rest " backend-rota "}
    {:name "backend-rota"}
    "Show"]
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
        (is (= expected (cmd/get-event-app-mention request)))))))

(def params-test-get-command
  [[request-app-mention
    {:user-id "U123"
     :command :create
     :args
     {:name "backend-rota"
      :users ["U435" "U567" "U789"]
      :description "Do what thou wilt shall be the whole of the Law"}}
    "Create command"]
   [{:params {:event {:text "  <@UNX01> show backend-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:user-id "UNX01"
     :command :show
     :args {:name "backend-rota"}}
    "Show command"]
   [{:params {:event {:text "  <@UNX01> show"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:user-id "UNX01"
     :command :show
     :args nil
     :error cmd/help-cmd-show}
    "Broken text 1"]
   [{:params {:event {:text "broken text"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}}}
    {:error cmd/help-msg}
    "Broken text 2"]])

(deftest test-get-command
  (testing "Get parsed command map"
    (doseq [[request expected description] params-test-get-command]
      (testing description
        (is (= expected (cmd/get-command request)))))))

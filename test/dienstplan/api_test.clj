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

(ns ^:integration
 dienstplan.api-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.java.jdbc :as jdbc]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [dienstplan.core]
   [dienstplan.commands :as cmd]
   [dienstplan.db]
   [dienstplan.slack :as slack]
   [hikari-cp.core :as cp]
   [mount.core :as mount :refer [defstate]]))

;; Fixtures

(defn fix-run-server
  [test]
  (log/info "[fix-run-server] start")
  (mount/start)
  (test)
  (mount/stop)
  (log/info "[fix-run-server] stop"))

(defn fix-mock-slack-api-request
  [test]
  (with-redefs
   [slack/slack-api-request (constantly {:ok? true :status 200 :date nil})]
    (test)))

(defstate db-test
  "Separate DB component with transaction rollback for tests"
  :start
  (let [db-opts (:db config)
        datasource (cp/make-datasource db-opts)]
    {:datasource datasource})
  :stop
  (-> db-test :datasource cp/close-datasource))

(defn fix-db-rollback
  [test]
  (log/info "[fix-db-rollback] start")
  (mount/start #'db-test)
  (jdbc/with-db-transaction [txn db-test]
    (jdbc/db-set-rollback-only! txn)
    (-> (mount/only [#'dienstplan.db/db])
        (mount/swap {#'dienstplan.db/db txn})
        (mount/start))
    (test)
    (mount/stop #'dienstplan.db/db))
  (mount/stop #'db-test)
  (log/info "[fix-db-rollback] stop"))

(use-fixtures :once fix-run-server fix-mock-slack-api-request)
(use-fixtures :each fix-db-rollback)

;; Tests

(def params-healthcheck
  [[{:method :get
     :url "http://localhost:8080/api/healthcheck"}
    {:status 200
     :body {:status "ok"}}
    "GET request with no params"]
   [{:method :get
     :url "http://localhost:8080/api/healthcheck?whatever=true"}
    {:status 200
     :body {:status "ok"}}
    "GET request with params"]
   [{:method :post
     :url "http://localhost:8080/api/healthcheck"}
    {:status 200
     :body {:status "ok"}}
    "POST request works too"]])

(deftest test-healthcheck
  (testing "Healthcheck API endpoint test"
    (doseq [[request response description] params-healthcheck]
      (testing description
        (let [{:keys [status body]} response
              actual (http/request request)
              actual-status (-> actual :status)
              actual-body (-> actual
                              :body
                              (json/parse-string true))]
          (is (= status actual-status))
          (is (= body actual-body)))))))

(def events-request-base
  {:method :post
   :url "http://localhost:8080/api/events"
   :content-type :json
   :accept :json})

(deftest test-events
  (testing "Events API endpoint test"
    (testing "Create new rota, check who is duty"
      (let [create-response
            (http/request
             (merge
              events-request-base
              {:body (json/generate-string
                      {:event
                       {:text "<@U001> create my-rota <@U123> <@U456> <@U789> Do what thou wilt shall be the whole of the Law"
                        :ts "1640250011.000100"
                        :team "T123"
                        :channel "C123"}})}))
            who-response
            (http/request
             (merge
              events-request-base
              {:body
               (json/generate-string
                {:event
                 {:text "<@U001> who my-rota"
                  :ts "1640250011.000100"
                  :team "T123"
                  :channel "C123"}})}))]
        (is (= 200 (:status create-response)))
        (is (=
             {:channel "C123"
              :text "Rotation `my-rota` for channel <#C123> created successfully"}
             (-> create-response
                 :body
                 (json/parse-string true))))
        (is (= 200 (:status who-response)))
        (is (=
             {:channel "C123"
              :text "Hey <@U123>, you are an on-call person for `my-rota` rotation.\nDo what thou wilt shall be the whole of the Law"}
             (-> who-response
                 :body
                 (json/parse-string true))))
        (testing "Assign a user, check who is duty"
          (let [assign-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> assign my-rota <@U789>"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                who-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> who my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))]
            (is (= 200 (:status assign-response)))
            (is (=
                 {:channel "C123"
                  :text "Assigned user <@U789> in rotation `my-rota` of channel <#C123>"}
                 (-> assign-response
                     :body
                     (json/parse-string true))))
            (is (= 200 (:status who-response)))
            (is (=
                 {:channel "C123"
                  :text "Hey <@U789>, you are an on-call person for `my-rota` rotation.\nDo what thou wilt shall be the whole of the Law"}
                 (-> who-response
                     :body
                     (json/parse-string true))))))
        (testing "Rotate, check who's duty"
          (let [rotate-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> rotate my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                who-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> who my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))]
            (is (= 200 (:status rotate-response)))
            (is (=
                 {:channel "C123"
                  :text "Users in rotation `my-rota` of channel <#C123> have been rotated from <@U789> to <@U123>"}
                 (-> rotate-response
                     :body
                     (json/parse-string true))))
            (is (= 200 (:status who-response)))
            (is (=
                 {:channel "C123"
                  :text "Hey <@U123>, you are an on-call person for `my-rota` rotation.\nDo what thou wilt shall be the whole of the Law"}
                 (-> who-response
                     :body
                     (json/parse-string true))))))
        (testing "Delete, check about, list, who commands"
          (let [delete-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> delete my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                who-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> who my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                rotate-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> rotate my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                assign-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> assign my-rota <@U123>"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                about-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> about my-rota"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                list-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> list"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                help-explicit-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> help"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
                help-implicit-response
                (http/request
                 (merge
                  events-request-base
                  {:body
                   (json/generate-string
                    {:event
                     {:text "<@U001> there is no such command!"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))]
            (is (=
                 {:channel "C123"
                  :text "Rotation `my-rota` has been deleted"}
                 (-> delete-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text "Rotation `my-rota` not found in channel <#C123>"}
                 (-> who-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text "No users found in rotation `my-rota` of channel <#C123>"}
                 (-> rotate-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text "User <@U123> is not found in rotation `my-rota` of channel <#C123>"}
                 (-> assign-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text "Rotation `my-rota` not found in channel <#C123>"}
                 (-> about-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text "No rotations found in channel <#C123>"}
                 (-> list-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text (cmd/get-help-message)}
                 (-> help-explicit-response
                     :body
                     (json/parse-string true))))
            (is (=
                 {:channel "C123"
                  :text cmd/help-msg}
                 (-> help-implicit-response
                     :body
                     (json/parse-string true))))))))))

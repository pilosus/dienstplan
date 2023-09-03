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

(ns dienstplan.api-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [dienstplan.commands :as cmd]
   [dienstplan.core]
   [dienstplan.db :as db]
   [dienstplan.fixture :as fix]
   [dienstplan.helpers :as helpers]
   [dienstplan.schedule :as schedule]
   [next.jdbc :as jdbc])
  (:import (java.time ZonedDateTime ZoneId)))

;; Const & Helpers

(def dt-before "2020-02-15T10:30:59.123000000-00:00")
(def dt-now "2021-02-15T10:30:59.123000000-00:00")
(def dt-after "2022-02-15T10:30:59.123000000-00:00")

(defn zoned-dt
  [dt-str]
  (ZonedDateTime/parse dt-str))

(defn inst->zoned-dt
  [inst]
  (.atZone inst (ZoneId/of "UTC")))

(defn boom! [& _]
  (throw (ex-info "Boom!" {:data nil})))

(def events-request-base
  {:method :post
   :url "http://localhost:8080/api/events"
   :content-type :json
   :accept :json})

(def events-request-create-body
  {:body
   (json/generate-string
    {:event
     {:text "<@U001> create my-rota <@U123> <@U456> <@U789> Do what thou wilt shall be the whole of the Law"
      :ts "1640250011.000100"
      :team "T123"
      :channel "C123"}})})

(def events-request-create
  (merge events-request-base events-request-create-body))

;; Fixtures

(use-fixtures :once fix/fix-server-run fix/fix-slack-api-request-mock)
(use-fixtures :each fix/fix-db-truncate)

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

;; create rota & who & shout

(deftest ^:integration test-create-rota-ok
  (testing "Create new rota, check who is duty"
    (let [create-response (http/request events-request-create)
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
          shout-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> shout my-rota"
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
      (is (= 200 (:status shout-response)))
      (is (=
           {:channel "C123"
            :text "<@U123>"}
           (-> shout-response
               :body
               (json/parse-string true)))))))

(deftest ^:integration test-create-rota-already-exists
  (testing "Create rota that already exists"
    (let [create-new-response
          (http/request
           (merge
            events-request-base
            {:body (json/generate-string
                    {:event
                     {:text "<@U001> create my-rota <@U123> <@U456> <@U789> Do what thou wilt shall be the whole of the Law"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))
          create-already-existing-response
          (http/request
           (merge
            events-request-base
            {:body (json/generate-string
                    {:event
                     {:text "<@U001> create my-rota <@U123> <@U456> <@U789> Do what thou wilt shall be the whole of the Law"
                      :ts "1640250011.000100"
                      :team "T123"
                      :channel "C123"}})}))]
      (is (= 200 (:status create-already-existing-response)))
      (is (=
           {:channel "C123"
            :text "Rotation `my-rota` for channel <#C123> already exists"}
           (-> create-already-existing-response
               :body
               (json/parse-string true)))))))

;; update rota & about rota

(deftest ^:integration test-update-rota-ok
  (testing "Update existing rota, show details about rota"
    (with-redefs
     [helpers/now-ts-sql
      (constantly (-> dt-now clojure.instant/read-instant-timestamp))]
      (let [create-response (http/request events-request-create)
            update-response
            (http/request
             (merge
              events-request-base
              {:body
               (json/generate-string
                {:event
                 {:text "<@U001> update my-rota <@U789> <@U456> New description"
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
                  :channel "C123"}})}))]
        (is (= 200 (:status create-response)))
        (is (=
             {:channel "C123"
              :text "Rotation `my-rota` for channel <#C123> created successfully"}
             (-> create-response
                 :body
                 (json/parse-string true))))
        (is (= 200 (:status update-response)))
        (is (=
             {:channel "C123"
              :text "Rotation `my-rota` for channel <#C123> updated successfully"}
             (-> update-response
                 :body
                 (json/parse-string true))))
        (is (= 200 (:status about-response)))
        (is (=
             {:channel "C123"
              :text "Rotation `my-rota` [2021-02-15]: <@U789> <@U456>.\nNew description"}
             (-> about-response
                 :body
                 (json/parse-string true))))))))

;; rotate rota

(deftest ^:integration test-rotate-rota-ok
  (testing "Rotate successfully"
    (let [create-response (http/request events-request-create)
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
                :channel "C123"}})}))]
      (is (= 200 (:status rotate-response)))
      (is (=
           {:channel "C123"
            :text "Users in rotation `my-rota` of channel <#C123> have been rotated from <@U123> to <@U456>"}
           (-> rotate-response
               :body
               (json/parse-string true)))))))

(deftest ^:integration test-rotate-rota-no-users-found
  (testing "Rotate with no users"
    (with-redefs
     [db/rotate-duty!
      (constantly
       {:users-count 0 :users-updated 0 :prev-duty nil :current-duty nil})]
      (let [create-response (http/request events-request-create)
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
                  :channel "C123"}})}))]
        (is (= 200 (:status rotate-response)))
        (is (=
             {:channel "C123"
              :text "No users found in rotation `my-rota` of channel <#C123>"}
             (-> rotate-response
                 :body
                 (json/parse-string true))))))))

(deftest ^:integration test-rotate-rota-other-error
  (testing "Rotate with no users"
    (with-redefs
     [db/rotate-duty!
      (constantly
       {:users-count 3 :users-updated 1 :prev-duty nil :current-duty nil})]
      (let [create-response (http/request events-request-create)
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
                  :channel "C123"}})}))]
        (is (= 200 (:status rotate-response)))
        (is (=
             {:channel "C123"
              :text "Failed to rotate users in rotation `my-rota` of channel <#C123>"}
             (-> rotate-response
                 :body
                 (json/parse-string true))))))))

;; assign rota

(deftest ^:integration test-assign-rota-ok
  (testing "Assign a user, check who is duty"
    (let [create-response (http/request events-request-create)
          assign-response
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
               (json/parse-string true)))))))

(deftest ^:integration test-assign-rota-error
  (testing "Assign a user, error"
    (with-redefs
     [db/assign! (constantly :user-not-found)]
      (let [create-response (http/request events-request-create)
            assign-response
            (http/request
             (merge
              events-request-base
              {:body
               (json/generate-string
                {:event
                 {:text "<@U001> assign my-rota <@U789>"
                  :ts "1640250011.000100"
                  :team "T123"
                  :channel "C123"}})}))]
        (is (= 200 (:status assign-response)))
        (is (=
             {:channel "C123"
              :text "User <@U789> is not found in rotation `my-rota` of channel <#C123>"}
             (-> assign-response
                 :body
                 (json/parse-string true))))))))

;; who rota

(deftest ^:integration test-who-rota-not-found
  (testing "Check who is duty in a non-existent rota"
    (let [who-response
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
      (is (= 200 (:status who-response)))
      (is (=
           {:channel "C123"
            :text "Rotation `my-rota` not found in channel <#C123>"}
           (-> who-response
               :body
               (json/parse-string true)))))))

;; shout rota

(deftest ^:integration test-shout-rota-not-found
  (testing "Shout out about a duty in a non-existent rota"
    (let [shout-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> shout my-rota"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))]
      (is (= 200 (:status shout-response)))
      (is (=
           {:channel "C123"
            :text "Rotation `my-rota` not found in channel <#C123>"}
           (-> shout-response
               :body
               (json/parse-string true)))))))

;; about rota

(deftest ^:integration test-about-rota-not-found
  (testing "About non-existent rota"
    (let [about-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> about my-rota"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))]
      (is (= 200 (:status about-response)))
      (is (=
           {:channel "C123"
            :text "Rotation `my-rota` not found in channel <#C123>"}
           (-> about-response
               :body
               (json/parse-string true)))))))

;; delete rota

(deftest ^:integration test-delete-rota-ok
  (testing "Delete rota"
    (let [create-response (http/request events-request-create)
          delete-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> delete my-rota"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))]
      (is (= 200 (:status delete-response)))
      (is (=
           {:channel "C123"
            :text "Rotation `my-rota` has been deleted"}
           (-> delete-response
               :body
               (json/parse-string true)))))))

(deftest ^:integration test-delete-rota-not-found
  (testing "Delete non-existent rota"
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
                :channel "C123"}})}))]
      (is (= 200 (:status delete-response)))
      (is (=
           {:channel "C123"
            :text "Rotation `my-rota` not found in channel <#C123>"}
           (-> delete-response
               :body
               (json/parse-string true)))))))

;; list rotas

(deftest ^:integration test-list-rota-ok
  (testing "List rota"
    (with-redefs
     [helpers/now-ts-sql
      (constantly (-> dt-now clojure.instant/read-instant-timestamp))]
      (let [create-response-1 (http/request events-request-create)
            create-response-2
            (http/request
             (merge
              events-request-base
              {:body
               (json/generate-string
                {:event
                 {:text "<@U001> create your-rota <@U123> Your description"
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
                  :channel "C123"}})}))]
        (is (= 200 (:status list-response)))
        (is (=
             {:channel "C123"
              :text "Rotations created in channel <#C123>:\n- `my-rota` [2021-02-15]\n- `your-rota` [2021-02-15]"}
             (-> list-response
                 :body
                 (json/parse-string true))))))))

(deftest ^:integration test-list-rota-not-found
  (testing "List rota in channel with no rotas"
    (let [list-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> list"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))]
      (is (= 200 (:status list-response)))
      (is (=
           {:channel "C123"
            :text "No rotations found in channel <#C123>"}
           (-> list-response
               :body
               (json/parse-string true)))))))

;; help messages

(deftest ^:integration test-help-message
  (testing "Show help messages, explicit and implicit"
    (let [help-explicit-response
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
            :text (cmd/get-help-message)}
           (-> help-explicit-response
               :body
               (json/parse-string true))))
      (is (=
           {:channel "C123"
            :text cmd/help-msg}
           (-> help-implicit-response
               :body
               (json/parse-string true)))))))

;; exceptions

(deftest ^:integration test-api-unhandled-exception
  (testing "API unhandled exception"
    (with-redefs
     [helpers/text-trim boom!]
      (let [request
            (merge
             events-request-base
             ;; clj-http client throws exceptions for "not-ok" statuses
             ;; let's override the behavior to handle exceptional statuses manually
             {:throw-exceptions false}
             {:body (json/generate-string
                     {:event
                      {:text "<@U001> create my-rota <@U123> <@U456> <@U789> Test description"
                       :ts "1640250011.000100"
                       :team "T123"
                       :channel "C123"}})})
            response (http/request request)]
        (is (= 500 (:status response)))
        (is (=
             {:error "Internal error"}
             (-> response
                 :body
                 (json/parse-string true))))))))

;; schedule

(deftest ^:integration test-schedule-ok
  (testing "Create new schedule"
    (let [schedule-create-rotate-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule create \"rotate my-rota\" 5 0 * * Mon-Fri"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-create-duplicate-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule create \"rotate my-rota\" 5 0 * * Mon-Fri"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-create-who-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule create \"who my-rota\" 0 9 * * Mon-Fri"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-list-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule list"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-delete-who-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule delete \"who my-rota\""
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-delete-rotate-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule delete \"rotate my-rota\""
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-delete-non-existent-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule delete \"shout my-rota\""
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))
          schedule-list-empty-response
          (http/request
           (merge
            events-request-base
            {:body
             (json/generate-string
              {:event
               {:text "<@U001> schedule list"
                :ts "1640250011.000100"
                :team "T123"
                :channel "C123"}})}))]
      ;; create `rotate my-rota`
      (is (= 200 (:status schedule-create-rotate-response)))
      (is (=
           {:channel "C123"
            :text "Executable `rotate my-rota` successfully scheduled with `5 0 * * Mon-Fri` (at minute 5, past hour 0, on every day of week from Monday through Friday, in every month)"}
           (-> schedule-create-rotate-response
               :body
               (json/parse-string true))))

      ;; duplicate: unique chanel-executable index violation
      (is (= 200 (:status schedule-create-duplicate-response)))
      (is (=
           {:channel "C123"
            :text "Duplicate schedule for `rotate my-rota` in the channel"}
           (-> schedule-create-duplicate-response
               :body
               (json/parse-string true))))

      ;; create `who my-rota`
      (is (= 200 (:status schedule-create-who-response)))
      (is (=
           {:channel "C123"
            :text "Executable `who my-rota` successfully scheduled with `0 9 * * Mon-Fri` (at minute 0, past hour 9, on every day of week from Monday through Friday, in every month)"}
           (-> schedule-create-who-response
               :body
               (json/parse-string true))))

      ;; list schedules
      (is (= 200 (:status schedule-list-response)))
      (is (=
           {:channel "C123"
            :text "- `rotate my-rota` with `5 0 * * Mon-Fri`\n- `who my-rota` with `0 9 * * Mon-Fri`"}
           (-> schedule-list-response
               :body
               (json/parse-string true))))

      ;; delete who
      (is (= 200 (:status schedule-delete-who-response)))
      (is (=
           {:channel "C123"
            :text "Scheduling for `who my-rota` successfully deleted"}
           (-> schedule-delete-who-response
               :body
               (json/parse-string true))))

      ;; delete rotate
      (is (= 200 (:status schedule-delete-rotate-response)))
      (is (=
           {:channel "C123"
            :text "Scheduling for `rotate my-rota` successfully deleted"}
           (-> schedule-delete-rotate-response
               :body
               (json/parse-string true))))

      ;; delete non-existent
      (is (= 200 (:status schedule-delete-non-existent-response)))
      (is (=
           {:channel "C123"
            :text "Schedule not found for params: {:channel \"C123\", :executable \"shout my-rota\", :crontab nil, :run_at nil}"}
           (-> schedule-delete-non-existent-response
               :body
               (json/parse-string true))))

      ;; list is empty
      (is (= 200 (:status schedule-list-empty-response)))
      (is (=
           {:channel "C123"
            :text "No schedules found in the channel"}
           (-> schedule-list-empty-response
               :body
               (json/parse-string true)))))))

(def params-test-schedule-explain
  [["0 22 * * *"
    "Crontab `0 22 * * *` means the executable will be run at minute 0, past hour 22, on every day, in every month"
    "ok"]
   ["67 22 * * *"
    (format "`schedule` command failed: `<crontab>` error: Value error in 'minute' field. Given value: [67, 68). Expected: [0, 60)\n\n%s" cmd/help-cmd-schedule)
    "Wrong value"]
   ["Mon-Fri"
    (format "`schedule` command failed: `<crontab>` error: Invalid crontab format\n\n%s" cmd/help-cmd-schedule)
    "Parding error"]])

(deftest ^:integration test-schedule-explain
  (testing "Explain a schedule"
    (doseq [[crontab expected description] params-test-schedule-explain]
      (testing description
        (let [command (format "<@U001> schedule explain %s" crontab)
              response (http/request
                        (merge
                         events-request-base
                         {:body
                          (json/generate-string
                           {:event
                            {:text command
                             :ts "1640250011.000100"
                             :team "T123"
                             :channel "C123"}})}))]
          (is (= 200 (:status response)))
          (is (= {:channel "C123" :text expected}
                 (-> response
                     :body
                     (json/parse-string true)))))))))

(deftest ^:integration test-schedule-runner-ok
  (testing "Test background task for schedule processing"
    (with-redefs
      ;; `get-current-dt` used for parsing crontab when generating dt sequence
     [org.pilosus.kairos/get-current-dt (constantly (zoned-dt dt-before))
       ;; `now-ts-sql` used for `db/schedules-get` when selecting events
      helpers/now-ts-sql (constantly (clojure.instant/read-instant-timestamp dt-now))]
      (let [executable-str "rotate my-rota"
            crontab-str "59 23 31 12 *"
            command (format "<@U001> schedule create \"%s\" %s" executable-str crontab-str)
            processed-events-before-scheduling (schedule/run nil)
            _ (http/request events-request-create)
            _ (http/request
               (merge
                events-request-base
                {:body
                 (json/generate-string
                  {:event
                   {:text command
                    :ts "1640250011.000100"
                    :team "T123"
                    :channel "C123"}})}))
            events (jdbc/with-transaction [conn db/db]
                     (db/schedules-get
                      conn
                      (-> dt-after clojure.instant/read-instant-timestamp)))
            processed-events-after-scheduling (schedule/run nil)]
        (is (= 0 processed-events-before-scheduling))
        (is (= 1 processed-events-after-scheduling))
        (is (= 1 (count events)))
        (let [{:keys [schedule/run_at
                      schedule/crontab
                      schedule/executable]} (first events)
              zoned-run-at (inst->zoned-dt run_at)]
          (is (= crontab-str crontab))
          (is (= executable-str executable))
          (is (= 59 (.getMinute zoned-run-at)))
          (is (= 23 (.getHour zoned-run-at)))
          (is (= 31 (.getDayOfMonth zoned-run-at)))
          ;; extract int from the Month enum
          (is (= 12 (-> zoned-run-at
                        .getMonth
                        .getValue)))
          (is (= 2020 (.getYear zoned-run-at))))))))

(deftest ^:integration test-schedule-runner-exception
  (testing "Schedule processing failed"
    (with-redefs
     [db/schedule-update! boom!
      org.pilosus.kairos/get-current-dt (constantly (zoned-dt dt-before))
      helpers/now-ts-sql (constantly (clojure.instant/read-instant-timestamp dt-now))]
      (let [command "<@U001> schedule create \"rotate my-rota\" 59 23 31 12 *"
            response (http/request
                      (merge
                       events-request-base
                       {:body
                        (json/generate-string
                         {:event
                          {:text command
                           :ts "1640250011.000100"
                           :team "T123"
                           :channel "C123"}})}))
            events-before-processing
            (jdbc/with-transaction [conn db/db]
              (db/schedules-get
               conn
               (-> dt-after
                   clojure.instant/read-instant-timestamp)))
            processed-events (schedule/run nil)
            events-after-processing
            (jdbc/with-transaction [conn db/db]
              (db/schedules-get
               conn
               (-> dt-after
                   clojure.instant/read-instant-timestamp)))]
        (is (= 1 (count events-before-processing)))
        (is (= 1 processed-events))
        (is (= 1 (count events-after-processing)))
        (let [event-before (first events-before-processing)
              event-after (first events-after-processing)]
          (is (= event-before event-after)))))))

(def params-schedule-command-invalid-args
  [["<@u001> schedule create who my rota 0 9 * * Mon-Fri"
    {:ok? false :error "`<executable>` cannot be parsed"}
    "Invalid executable, double quotes omitted"]
   ["<@u001> schedule create \"who my rota\" Mon-Fri"
    {:ok? false :error "`<crontab>` error: Invalid crontab format"}
    "Invalid crontab"]])

(deftest ^:integration test-schedule-command-invalid-args
  (testing "Invalid `schedule` command arguments: "
    (doseq [[command invalid-arg description] params-schedule-command-invalid-args]
      (testing description
        (let [response (http/request
                        (merge
                         events-request-base
                         {:body
                          (json/generate-string
                           {:event
                            {:text command
                             :ts "1640250011.000100"
                             :team "T123"
                             :channel "C123"}})}))
              actual (-> response
                         :body
                         (json/parse-string true)
                         :text)
              expected (cmd/fmt-schedule-invalid-arg invalid-arg)]
          (is (= expected actual)))))))

(deftest ^:integration test-schedule-command-duplicate
  (testing "Duplicate schedule"
    (let [executable "rotate my-rota"
          crontab "0 9 * * Mon-Fri"
          explain "at minute 0, past hour 9, on every day of week from Monday through Friday, in every month"
          command (format "<@U001> schedule create \"%s\" %s"
                          executable
                          crontab)
          request (merge
                   events-request-base
                   {:body
                    (json/generate-string
                     {:event
                      {:text command
                       :ts "1640250011.000100"
                       :team "T123"
                       :channel "C123"}})})
          create-response (http/request request)
          duplicate-response (http/request request)
          created (-> create-response
                      :body
                      (json/parse-string true)
                      :text)
          duplicate (-> duplicate-response
                        :body
                        (json/parse-string true)
                        :text)]
      (is (= created (format "Executable `%s` successfully scheduled with `%s` (%s)"
                             executable crontab explain)))
      (is (= duplicate (format "Duplicate schedule for `%s` in the channel"
                               executable))))))

(def params-schedule-command-db-exceptions
  [["<@U001> schedule create \"rotate my-rota\" 0 9 * * Mon-Fri"
    "Boom!"
    "Error on schedule insert"]
   ["<@U001> schedule delete \"rotate my-rota\""
    "Boom!"
    "Error on schedule delete"]
   ["<@U001> schedule list"
    "Boom!"
    "Error on listing schedules"]])

(deftest ^:integration test-schedule-command-db-exceptions
  (testing "DB exception for schedule subcommand: "
    (with-redefs [next.jdbc.sql/insert! boom!
                  next.jdbc.sql/delete! boom!
                  next.jdbc/execute! boom!]
      (doseq [[command expected description] params-schedule-command-db-exceptions]
        (testing description
          (let [request (merge
                         events-request-base
                         {:body
                          (json/generate-string
                           {:event
                            {:text command
                             :ts "1640250011.000100"
                             :team "T123"
                             :channel "C123"}})})
                response (http/request request)
                actual (-> response
                           :body
                           (json/parse-string true)
                           :text)]
            (is (= expected actual))))))))

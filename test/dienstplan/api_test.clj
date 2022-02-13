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

(deftest test-events
  (testing "Events API endpoint test"
    (testing "Create new rota"
      (let [create-new-request-body
            (json/generate-string
             {:event
              {:text "<@U001> create my-rota <@U123> <@U456> <@U789> Do what thou wilt shall be the whole of the Law"
               :ts "1640250011.000100"
               :team "T123"
               :channel "C123"}})
            create-new-request
            {:method :post
             :url "http://localhost:8080/api/events"
             :content-type :json
             :accept :json
             :body create-new-request-body}
            create-new-response (http/request create-new-request)
            create-existing-response (http/request create-new-request)]
        (is (= 200 (:status create-new-response)))
        (is (=
             {:channel "C123"
              :text "Rotation `my-rota` for channel <#C123> created successfully"}
             (-> create-new-response
                 :body
                 (json/parse-string true))))
        (is (= 200 (:status create-existing-response)))
        (is (=
             {:channel "C123"
              :text "Rotation `my-rota` for channel <#C123> already exists"}
             (-> create-existing-response
                 :body
                 (json/parse-string true))))))))

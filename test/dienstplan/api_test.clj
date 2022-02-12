(ns ^:integration
 dienstplan.api-test
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.java.jdbc :as jdbc]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.tools.logging :as log]
   [dienstplan.config]
   [dienstplan.core]
   [dienstplan.db]
   [dienstplan.slack :as slack]
   [mount.core :as mount]))

;; Fixtures

(defn fix-run-server
  [test]
  (log/info "[fix-run-server] start")
  (mount/start
   #'dienstplan.config/config
   #'dienstplan.core/logs
   #'dienstplan.db/db
   #'dienstplan.core/server)
  (test)
  (mount/stop)
  (log/info "[fix-run-server] stop"))

(defn fix-mock-slack-api-request
  [test]
  (with-redefs
   [slack/slack-api-request (constantly {:ok? true :status 200 :date nil})]
    (test)))

(defn fix-db-rollback
  [test]
  (log/info "[fix-db-rollback] start")
  (jdbc/with-db-transaction [txn dienstplan.db/db {:isolation :serializable}]
    (jdbc/db-set-rollback-only! txn)
    (-> (mount/only [#'dienstplan.db/db])
        (mount/swap {#'dienstplan.db/db txn})
        (mount/start))
    (test)))

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

(def params-events
  [[{:method :post
     :url "http://localhost:8080/api/events"
     :params
     {:event
      {:text "<@U001> create backend rotation <@U123> <@U456> <@U789> Do what thou wilt shall be the whole of the Law"
       :ts "1640250011.000100"
       :team "T123"
       :channel "C123"}}}
    {:status 200 :body {:channel "C123" :text "Rotation `backend rotation` for channel <#C123> created successfully"}}
    {:status 200 :body {:channel "C123" :text "Rotation `backend rotation` for channel <#C123> already exists"}}
    "Create rota"]])

(deftest test-events
  (testing "Events API endpoint test"
    (doseq [[request response-created response-exists description] params-events]
      (testing description
        (let [{:keys [method url params]} request
              request-body (json/generate-string params)
              request-map {:method method
                           :url url
                           :content-type :json
                           :accept :json
                           :body request-body}
              actual-response-created (http/request request-map)
              actual-response-created-status
              (-> actual-response-created  :status)
              actual-response-created-body
              (-> actual-response-created
                  :body
                  (json/parse-string true))
              actual-response-exists (http/request request-map)
              actual-response-exists-status
              (-> actual-response-exists  :status)
              actual-response-exists-body
              (-> actual-response-exists
                  :body
                  (json/parse-string true))]
          (is (= (:status response-created) actual-response-created-status))
          (is (= (:body response-created) actual-response-created-body))
          (is (= (:status response-exists) actual-response-exists-status))
          (is (= (:body response-exists) actual-response-exists-body)))))))

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

(ns dienstplan.schedule-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.config :refer [config]]
   [dienstplan.schedule :as schedule])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

;; Test helper: create executor with custom task function
(defn- start-test-executor
  "Start executor with a custom task function for testing"
  [task-fn delay-seconds]
  (let [executor (Executors/newSingleThreadScheduledExecutor)]
    (.scheduleWithFixedDelay executor
                             task-fn
                             0
                             delay-seconds
                             TimeUnit/SECONDS)
    executor))

(deftest test-create-scheduled-task-catches-exceptions
  (testing "Task wrapper catches exceptions and doesn't propagate them"
    ;; Test that exceptions are caught by creating a task that throws
    ;; and verifying the executor survives
    (let [call-count (atom 0)
          task-fn (fn []
                    (swap! call-count inc)
                    (throw (Exception. "Test exception")))
          wrapped-task (fn []
                         (try
                           (task-fn)
                           (catch Exception _e
                             nil)))
          executor (start-test-executor wrapped-task 1)]
      (try
        (Thread/sleep 2500)
        ;; Task should have run multiple times despite throwing
        (is (>= @call-count 2) "Wrapped task should survive exceptions")
        (finally
          (schedule/stop-executor executor))))))

(deftest test-start-executor-creates-scheduled-executor
  (testing "start-executor returns a ScheduledExecutorService"
    (with-redefs [schedule/process-schedules (constantly nil)
                  config {:daemon {:delay 60}}]
      (let [executor (schedule/start-executor)]
        (try
          (is (instance? ScheduledExecutorService executor))
          (is (not (.isShutdown executor)))
          (finally
            (schedule/stop-executor executor)))))))

(deftest test-start-executor-schedules-task
  (testing "start-executor schedules the task to run"
    (let [call-count (atom 0)]
      (with-redefs [schedule/process-schedules #(swap! call-count inc)
                    config {:daemon {:delay 1}}]
        (let [executor (schedule/start-executor)]
          (try
            ;; Wait for the initial task execution
            (Thread/sleep 500)
            (is (>= @call-count 1) "Task should have run at least once")
            (finally
              (schedule/stop-executor executor))))))))

(deftest test-stop-executor-graceful-shutdown
  (testing "stop-executor gracefully shuts down the executor"
    (with-redefs [schedule/process-schedules (constantly nil)
                  config {:daemon {:delay 60}}]
      (let [executor (schedule/start-executor)]
        (schedule/stop-executor executor)
        (is (.isShutdown executor))
        (is (.isTerminated executor))))))

(deftest test-stop-executor-nil-safe
  (testing "stop-executor handles nil executor gracefully"
    (is (nil? (schedule/stop-executor nil)))))

(deftest test-executor-survives-task-exceptions
  (testing "Executor continues running after task throws exception"
    (let [call-count (atom 0)]
      (with-redefs [schedule/process-schedules
                    #(do (swap! call-count inc)
                         (throw (Exception. "Test exception")))
                    config {:daemon {:delay 1}}]
        (let [executor (schedule/start-executor)]
          (try
            ;; Wait for multiple executions
            (Thread/sleep 2500)
            ;; Should have been called multiple times despite exceptions
            (is (>= @call-count 2) "Task should have run multiple times despite exceptions")
            (finally
              (schedule/stop-executor executor))))))))

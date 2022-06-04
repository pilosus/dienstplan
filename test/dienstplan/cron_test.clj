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

(ns dienstplan.cron-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.cron :as cron]))

(deftest test-get-ts-no-args
  (testing "Test get current ts"
    (is (= (type (cron/get-ts)) java.time.ZonedDateTime))))

(def params-cron-parsed-ok
  [["12,14,17,35-45/3 */2 27 2 *"
    {:minute '(12 14 17 35 38 41 44),
     :hour '(0 2 4 6 8 10 12 14 16 18 20 22),
     :day-of-month '(27),
     :month '(2),
     :day-of-week '()}
    "At minute 12, 14, 17, and every 3rd minute from 35 through 45 past every 2nd hour on day-of-month 27 in February"]
   ["39 9 * * wed-fri"
    {:minute '(39),
     :hour '(9),
     :day-of-month '(),
     :month '(1 2 3 4 5 6 7 8 9 10 11 12),
     :day-of-week '(3 4 5)}
    "At 09:39 on every day-of-week from Wednesday through Friday"]
   ["0 10 3,7 Dec Mon"
    {:minute '(0),
     :hour '(10),
     :day-of-month '(3 7),
     :month '(12),
     :day-of-week '(1)}
    "At 10:00, on 3rd and 7th or every Monday of December"]
   ["* * * * *"
    {:minute '(0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19
                 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40
                 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59),
     :hour '(0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23),
     :day-of-month '(1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19
                       20 21 22 23 24 25 26 27 28 29 30 31),
     :month '(1 2 3 4 5 6 7 8 9 10 11 12),
     :day-of-week '(1 2 3 4 5 6 7)}
    "At every minute"]
   ["0 10 3,7 12 Tue-Sat * * *"
    {:minute '(0),
     :hour '(10),
     :day-of-month '(3 7),
     :month '(12),
     :day-of-week '(2 3 4 5 6)}
    "Excessive elements ignored"]])

(deftest test-parse-cron-ok
  (testing "Test successful parsing cron string into a map"
    (doseq [[crontab expected description] params-cron-parsed-ok]
      (testing description
        (is (= expected (cron/parse-cron crontab)))))))

(def params-cron-parsed-fail
  [["something */2 27 2 *"
    {:minute '(),
     :hour '(0 2 4 6 8 10 12 14 16 18 20 22),
     :day-of-month '(27),
     :month '(2),
     :day-of-week '()}
    "Minutes are incorrect"]
   ["what's up 3,7 Dec Mon"
    {:minute '(),
     :hour '(),
     :day-of-month '(3 7),
     :month '(12),
     :day-of-week '(1)}
    "Minutes and hours are incorrect"]
   ["crontab goes brrrr"
    nil
    "The whole crontab string is incorrect"]])

(deftest test-parse-cron-fail
  (testing "Test failing to parse cron string"
    (doseq [[crontab expected description] params-cron-parsed-fail]
      (testing description
        (is (= (cron/parse-cron crontab)))))))

(def params-get-next-tss
  [["12,14,17,35-45/3 */2 27 2 *"
    (cron/get-ts [1970 1 1 0 0])
    10
    [(cron/get-ts [1970 2 27 0 12])
     (cron/get-ts [1970 2 27 0 14])
     (cron/get-ts [1970 2 27 0 17])
     (cron/get-ts [1970 2 27 0 35])
     (cron/get-ts [1970 2 27 0 38])
     (cron/get-ts [1970 2 27 0 41])
     (cron/get-ts [1970 2 27 0 44])
     (cron/get-ts [1970 2 27 2 12])
     (cron/get-ts [1970 2 27 2 14])
     (cron/get-ts [1970 2 27 2 17])]
    "At minute 12, 14, 17, and every 3rd minute from 35 through 45 past every 2nd hour on day-of-month 27 in February"]
   ["39 9 * * wed-fri"
    (cron/get-ts [1970 1 1 0 0])
    8
    [(cron/get-ts [1970 1 1 9 39])
     (cron/get-ts [1970 1 2 9 39])
     (cron/get-ts [1970 1 7 9 39])
     (cron/get-ts [1970 1 8 9 39])
     (cron/get-ts [1970 1 9 9 39])
     (cron/get-ts [1970 1 14 9 39])
     (cron/get-ts [1970 1 15 9 39])
     (cron/get-ts [1970 1 16 9 39])]
    "At 09:39 on every day-of-week from Wednesday through Friday"]
   ["0 10 3,7 Dec Mon"
    (cron/get-ts [1970 1 1 0 0])
    10
    [(cron/get-ts [1970 12 3 10 0])
     (cron/get-ts [1970 12 7 10 0])
     (cron/get-ts [1970 12 14 10 0])
     (cron/get-ts [1970 12 21 10 0])
     (cron/get-ts [1970 12 28 10 0])
     (cron/get-ts [1971 12 3 10 0])
     (cron/get-ts [1971 12 6 10 0])
     (cron/get-ts [1971 12 7 10 0])
     (cron/get-ts [1971 12 13 10 0])
     (cron/get-ts [1971 12 20 10 0])]
    "At 10:00, on 3rd and 7th or every Monday of December"]
   ["0 0 * May 0-3"
    (cron/get-ts [1970 1 1 0 0])
    8
    [(cron/get-ts [1970 5 3 0 0])
     (cron/get-ts [1970 5 4 0 0])
     (cron/get-ts [1970 5 5 0 0])
     (cron/get-ts [1970 5 6 0 0])
     (cron/get-ts [1970 5 10 0 0])
     (cron/get-ts [1970 5 11 0 0])
     (cron/get-ts [1970 5 12 0 0])
     (cron/get-ts [1970 5 13 0 0])]
    "At 00:00, Wednesday through Sunday of May"]
   ["0 0 * May 1,2,3,7"
    (cron/get-ts [1970 1 1 0 0])
    8
    [(cron/get-ts [1970 5 3 0 0])
     (cron/get-ts [1970 5 4 0 0])
     (cron/get-ts [1970 5 5 0 0])
     (cron/get-ts [1970 5 6 0 0])
     (cron/get-ts [1970 5 10 0 0])
     (cron/get-ts [1970 5 11 0 0])
     (cron/get-ts [1970 5 12 0 0])
     (cron/get-ts [1970 5 13 0 0])]
    "At 00:00, Monday, Tuesday, Wednesday, Sunday of May"]
   ["* * * * *"
    (cron/get-ts [1970 1 1 0 0])
    5
    [(cron/get-ts [1970 1 1 0 1])
     (cron/get-ts [1970 1 1 0 2])
     (cron/get-ts [1970 1 1 0 3])
     (cron/get-ts [1970 1 1 0 4])
     (cron/get-ts [1970 1 1 0 5])]
    "At every minute"]])

(deftest test-get-next-tss
  (testing "Test getting next timestamps"
    (doseq [[crontab ts quantity expected description] params-get-next-tss]
      (testing description
        (is (= expected (take quantity (cron/get-next-tss ts crontab))))))))

(deftest test-get-next-tss-no-ts
  (testing "Test getting next timestamp for the current ts"
    (is
     (= (type (first (cron/get-next-tss "0 10 26 5 *"))))
     java.time.ZonedDateTime)))

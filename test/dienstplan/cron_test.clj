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

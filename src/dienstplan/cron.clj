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

(ns dienstplan.cron
  "Crontab format parsing

  Format follows that of the Vixie cron:
  https://linux.die.net/man/5/crontab

  including support for the following timestamp matching:
  (month AND hour AND minute AND (day-of-month OR day-of-week))

  Short 3-letter names for months and week days are supported, e.g. Jan, Wed.
  Sunday's day of week number can be either 0 or 7."
  (:gen-class)
  (:require
   [clojure.string :as string]
   [tick.core :as t]
   [java-time :as jt]))

;; Constants

(def year-limit 100)

(def skip-range "")

(def list-regex #",\s*")

(def range-regex
  #"(?s)(?<start>([0-9|*]+))(?:-(?<end>([0-9]+)))?(?:/(?<step>[0-9]+))?")

(def substitute-values
  {#"mon" "1" #"tue" "2" #"wed" "3" #"thu" "4" #"fri" "5" #"sat" "6" #"sun" "7"
   #"jan" "1" #"feb" "2" #"mar" "3" #"apr" "4" #"may" "5" #"jun" "6" #"jul" "7"
   #"aug" "8" #"sep" "9" #"oct" "10" #"nov" "11" #"dec" "12"})

(def range-values
  {:minute {:start 0 :end (+ 59 1)}
   :hour {:start 0 :end (+ 23 1)}
   :day-of-month {:start 1 :end (+ 31 1)}
   :month {:start 1 :end (+ 12 1)}
   :day-of-week {:start 1 :end (+ 7 1)}})

;; Collection helpers

(defn in?
  "Return true if a collection contains the element"
  [coll e]
  (if (some #(= e %) coll) true false))

;; Timestamp helpers

(defn get-ts
  "Get current or given timestamp for UTC timezone"
  ^java.time.ZonedDateTime
  ([] (get-ts []))
  ([args] (jt/with-zone-same-instant (apply jt/zoned-date-time args) "UTC")))

(defn ts-day-ok?
  "Return true if timestamp's day of month or day of week are in given sequenses"
  [^java.time.ZonedDateTime ts
   days-of-month
   days-of-week]
  (let [day-of-month (jt/as ts :day-of-month)
        day-of-week (jt/as ts :day-of-week)
        contains-day-of-month? (in? days-of-month day-of-month)
        contains-day-of-week? (in? days-of-week day-of-week)]
    (or contains-day-of-month? contains-day-of-week?)))

;; Parsing

(defn parse-range
  [^String s
   ^clojure.lang.Keyword type]
  "Parse range of values with possible step value"
  (let [matcher (re-matcher range-regex s)]
    (if (.matches matcher)
      (let [start (.group matcher "start")
            range-start
            (cond
              (= start "*") (get-in range-values [type :start])
              :else (Integer/parseInt (.group matcher "start")))

            step (.group matcher "step")
            range-step (if step (Integer/parseInt step) 1)

            end (.group matcher "end")
            range-end
            (cond
              end (+ (Integer/parseInt end) 1)
              (or step (= start "*")) (get-in range-values [type :end])
              :else (+ range-start 1))]
        (range range-start range-end range-step))
      nil)))

(defn parse-value
  "Parse single value (e.g. minutes, hours, months, etc.)"
  [^String s
   ^clojure.lang.Keyword type]
  (let [ranges (string/split s list-regex)
        values (map #(parse-range % type) ranges)
        flat (apply concat values)
        sorted (sort flat)]
    sorted))

(defn- replace-names-with-numbers
  [s substitutions]
  (if (empty? substitutions)
    s
    (let [[match replacement] (first substitutions)
          s-replaced (string/replace s match replacement)]
      (recur s-replaced (rest substitutions)))))

(defn names->numbers
  "Replace month and day of week names with respective numeric values"
  [^String s]
  (replace-names-with-numbers s (seq substitute-values)))

(defn- substitute-zero-day-of-week
  "Substitute 0 day of week for Sunday with 7 and return sorted sequence"
  [day-of-week-range]
  (->>
   day-of-week-range
   (map #(if (= % 0) 7 %))
   sort))

(defn parse-cron
  "Parse crontab string"
  [^String s]
  (try
    (let
     [[minute hour day-of-month month day-of-week]
      (-> s
          string/trim
          string/lower-case
          names->numbers
          (string/split #"\s+"))
      day-of-month'
      (cond
        (and (= day-of-month "*") (not (= day-of-week "*"))) skip-range
        :else day-of-month)
      day-of-week'
      (cond
        (and (= day-of-week "*") (not (= day-of-month "*"))) skip-range
        :else day-of-week)
      cron
      {:minute (parse-value minute :minute)
       :hour (parse-value hour :hour)
       :day-of-month (parse-value day-of-month' :day-of-month)
       :month (parse-value month :month)
       :day-of-week
       (->
        day-of-week'
        (parse-value :day-of-week)
        substitute-zero-day-of-week)}]
      cron)
    (catch Exception _ nil)))

;; Business layer

(defn get-timeseries
  "Generate timestamps for given years range for a parsed crontab"
  [start-year cron-parsed]
  (when cron-parsed
    (let [day-of-month (:day-of-month cron-parsed)
          day-of-week (:day-of-week cron-parsed)
          days (range
                (get-in range-values [:day-of-month :start])
                (get-in range-values [:day-of-month :end]))]
      ;; Infinite years seq doesn't terminate the loop in case
      ;; at least one other value (e.g. minute) is an empty sequence
      ;; even when take/drop/filter or the like functions applied.
      ;; That's why we take N elements of the infinite seq only.
      ;; The behvaiour doesn't reproduce when all the values are non-empty/nil
      ;; Is it a Clojure bug?
      (for [year (take year-limit (iterate inc start-year))
            month (:month cron-parsed)
            day days
            hour (:hour cron-parsed)
            minute (:minute cron-parsed)
            :let [ts
                  (try
                    (->
                     (jt/local-date-time year month day hour minute)
                     (t/in "UTC"))
                    ;; Skip incorrect datetimes like February 29th
                    (catch java.time.DateTimeException _ nil))]
            :when (and
                   (some? ts)
                   (ts-day-ok? ts day-of-month day-of-week))]
        ts))))

(defn get-next-tss
  "Parse crontab string and return lazy seq of timestamps for next exucutions"
  ([s] (get-next-tss (get-ts) s))
  ([ts s]
   (let [start-year (jt/as ts :year)]
     (->>
      s
      parse-cron
      (get-timeseries start-year)
      ;; TODO optimize filtering
      ;; Generate many timestamps, filtering enabled
      ;; > (time (first (get-next-tss (get-ts [2022 12 31 23 59 59]) "* * * * *")))
      ;; "Elapsed time: 2207.318429 msecs"
      ;; Generate less timestamps since beginning of the year (1 month, not 12)
      ;; > (time (first (get-next-tss (get-ts [2022 1 31 23 59 59]) "* * * * *")))
      ;; "Elapsed time: 178.73676 msecs"
      ;; Generate even less ts by passing crontab of low frequency
      ;; > (time (first (get-next-tss (get-ts [2022 1 31 23 59 59]) "0 10 1 * *")))
      ;; "Elapsed time: 0.517044 msecs"
      ;; Generate many ts, filtering disabled (commented out)
      ;; > (time (first (get-next-tss (get-ts [2022 12 31 23 59 59]) "* * * * *")))
      ;; "Elapsed time: 0.379484 msecs"
      ;; Generate many ts, filtering changed to function (constantly true)
      ;; > (time (first (get-next-tss (get-ts [2022 12 31 23 59 59]) "* * * * *")))
      ;; "Elapsed time: 0.488917 msecs"
      ;; Conclusion: jt/after? is very slow
      ;; TODO: consider writing own ts comparator instead of jt/after?
      (filter #(jt/after? % ts))))))

(ns dienstplan.cron
  (:gen-class)
  (:require
   [clojure.string :as string]
   [tick.core :as t]
   [java-time :as jt]))

;; https://crontab.guru/crontab.5.html

;; https://stackoverflow.com/questions/34357126/why-crontab-uses-or-when-both-day-of-month-and-day-of-week-specified
;; (month AND hour AND minute AND (mday OR wday))

;; "0 10 3,7 * Mon" will execute on 10:00, 3rd AND 7th AND every Monday

;; Sunday - 0

(def skip-range "")

(def list-regex #",\s*")

(def range-regex
  #"(?s)(?<start>([0-9|*]+))(?:-(?<end>([0-9]+)))?(?:/(?<step>[0-9]+))?")

(def substitute-values
  {#"mon" "1"
   #"tue" "2"
   #"wed" "3"
   #"thu" "4"
   #"fri" "5"
   #"sat" "6"
   #"sun" "7"
   #"jan" "1"
   #"feb" "2"
   #"mar" "3"
   #"apr" "4"
   #"may" "5"
   #"jun" "6"
   #"jul" "7"
   #"aug" "8"
   #"sep" "9"
   #"oct" "10"
   #"nov" "11"
   #"dec" "12"})

(def range-values
  {:minute {:start 0 :end (+ 59 1)}
   :hour {:start 0 :end (+ 23 1)}
   :day-of-month {:start 1 :end (+ 31 1)}
   :month {:start 1 :end (+ 12 1)}
   :day-of-week {:start 0 :end (+ 6 1)}})

;; Temporary examples
;; Move to untitests once debugged

(def c0
  "At minute 12, 14, 17, and every 3rd minute from 35 through 45 past every 2nd hour on day-of-month 27 in February. Next: 2023-02-27 00:12:00"
  "12,14,17,35-45/3 */2 27 2 *")

(def c1
  "12,14,17,35,38,41,44 minutes past the hour every 2 hours"
  "12,14,17,35-45/3 */2 * * *")

(def c2
  "23:17 and 23:45 every day"
  "17,45 23 * * *")

(def c3
  "At 01:00 on Sunday"
  "0 1 * * SUN")

(def c4
  "At 09:39 on every day-of-week from Wednesday through Friday"
  "39 9 * * wed-fri")

(def c5
  "At 09:39 on day-of-month 1 in February"
  "39 9 1 2 *")

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

(defn parse-cron
  "Parse crontab string"
  [^String s]
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
    _ (prn (format "%s %s %s %s %s" minute hour day-of-month month day-of-week))
    cron
    {:minute (parse-value minute :minute)
     :hour (parse-value hour :hour)
     :day-of-month (parse-value day-of-month' :day-of-month)
     :month (parse-value month :month)
     :day-of-week (parse-value day-of-week' :day-of-week)}]
    cron))

(defn get-now
  "Get current timestamp for UTC timezone

  See more:
  https://github.com/dm3/clojure.java-time"
  ^java.time.ZonedDateTime
  []
  (jt/with-zone-same-instant (jt/zoned-date-time) "UTC"))

(defn in?
  "Return true if a collection contains the element"
  [coll e]
  (if (some #(= e %) coll) true false))

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

(defn generate-timeseries
  "Generate seq of java.time.ZonedDateTime timestamps for a parsed crontab"
  [cron-parsed]
  (let [now-year (jt/as (get-now) :year)
        eleven-years-later (+ now-year 11)
        day-of-month (:day-of-month cron-parsed)
        day-of-week (:day-of-week cron-parsed)
        days (range
              (get-in range-values [:day-of-month :start])
              (get-in range-values [:day-of-month :end]))]
    (for [year (range now-year eleven-years-later)  ;; ten years range
          month (:month cron-parsed)
          ;; day (:day-of-month cron-parsed)
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
      ts)))

(defn find-first
  "Return first element in the collection that meets predicate conditions

  Example:
  > (find-first #(< % 10) [30 20 11 10 9 8])
  9
  "
  [f coll]
  (first (filter f coll)))

;; Entrypoint

(defn get-next-ts
  "Parse crontab string and return a timestamp for the next cron invocation"
  [^String s]
  (->>
   s
   parse-cron
   generate-timeseries
   (find-first #(jt/after? % (get-now)))))

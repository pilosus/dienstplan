(ns dienstplan.cron
  (:gen-class)
  (:require
   [clojure.string :as string]))

;; https://crontab.guru/crontab.5.html

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
   #"sun" "0"
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

(def c1
  "12,14,17,35,38,41,44 minutes past the hour every 2 hours"
  "12,14,17,35-45/3 */2 * * *" )

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

(defn parse-range
  [s type]
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
  [s type]
  (let [ranges (string/split s list-regex)
        values (map #(parse-range % type) ranges)
        result (apply concat values)]
    result))

(defn- replace-names-with-numbers
  [s substitutions]
  (if (empty? substitutions)
    s
    (let [[match replacement] (first substitutions)
          s-replaced (string/replace s match replacement)]
      (recur s-replaced (rest substitutions)))))

(defn names->numbers
  "Replace month and day of week names with respective numeric values"
  [s]
  (replace-names-with-numbers s (seq substitute-values)))

(defn parse-cron
  "Parse crontab string"
  [s]
  (let
      [[minute hour day-of-month month day-of-week]
       (-> s
           string/trim
           string/lower-case
           names->numbers
           (string/split #"\s+"))
       _ (prn (format "%s %s %s %s %s" minute hour day-of-month month day-of-week))
       cron
       {:minute (parse-value minute :minute)
        :hour (parse-value hour :hour)
        :day-of-month (parse-value day-of-month :day-of-month)
        :month (parse-value month :month)
        :day-of-week (parse-value day-of-week :day-of-week)}]
    cron))

;; TODO
(defn parse-ts
  "Parse timestamp into map of parts matching cron format"
  [ts]
  ts)

;; TODO
(defn get-next-ts
  "Get timestamp after given one according to crontab"
  [cron-parsed ts]
  ts)

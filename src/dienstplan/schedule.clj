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

(ns dienstplan.schedule
  (:require
   [clojure.tools.logging :as log]
   [clojure.string :as s]
   [dienstplan.commands :as commands]
   [dienstplan.db :as db]
   [dienstplan.helpers :as helpers]
   [mount.core :as mount]
   [next.jdbc :as jdbc]))

(defmacro with-timer
  "Return a map with function evaluation results and time elapsed"
  [body]
  `(let [text# (new java.io.StringWriter)]
     (binding [*out* text#]
       (let [result# (time ~body)
             elapsed# (-> text#
                          str
                          (s/replace #"\"(.*)\"\n" "$1"))]
         {:result result#
          :time elapsed#}))))

(defn- request-map
  "Get a request hashmap to run the bot command with"
  [channel text]
  {:params
   {:event
    {:channel channel
     :text text
     :ts (-> (helpers/now-ts-seconds) float str)}}})

(defn process-rows
  "Iterate over rows from `schedule` table, process them, return number
  of processed rows"
  [^java.sql.Connection conn rows fns]
  (let [{:keys
         [fn-shout-executable
          fn-process-executable
          fn-update-schedule]} fns]
    (loop [events (seq rows)
           processed 0]
      (if events
        (let [event (first events)]
          (log/debug "Start processing event" event)

          (try
            (fn-shout-executable conn event)
            (fn-process-executable conn event)
            (fn-update-schedule conn event)

            (catch Exception e
              (log/errorf e "Couldn't process row %s: %s" event (.getMessage e))
              (.rollback conn)))

          (log/debug "Event processed")
          (recur (next events) (inc processed)))
        processed))))

(defn process-events
  "Process scheduled events, return number of processed events"
  [fns]
  (jdbc/with-transaction [conn db/db]
    (log/info "Start processing scheduled events")
    (let [fn-get-schedules (get fns :fn-get-schedules)
          rows (fn-get-schedules conn nil)
          {:keys [result time]} (with-timer (process-rows conn rows fns))]
      (if (> result 0)
        (do (log/infof "Processed: %s event(s). %s" result time) result)
        (do (log/infof "No scheduled events found. %s" time) 0)))))

;; Wrappers
;; We need a <@PLACEHOLDER> in place of a bot mention to match the regex

(defn wrap-get-schedules
  [conn _]
  (db/schedules-get conn (helpers/now-ts-sql)))

(defn wrap-shout-executable
  [_ schedule-row]
  (let [{:keys [schedule/channel
                schedule/executable
                schedule/crontab]} schedule-row
        text (format "<@PLACEHOLDER> schedule shout \"%s\" %s" executable crontab)
        request (request-map channel text)]
    (log/debug "Notify about executing of schedule")
    (commands/send-command-response! request)))

(defn wrap-process-executable
  [_ schedule-row]
  (let [{:keys [schedule/channel
                schedule/executable]} schedule-row
        text (format "<@PLACEHOLDER> %s" executable)
        request (request-map channel text)]
    (log/debug "Process scheduled executable" request)
    (commands/send-command-response! request)))

(defn wrap-update-schedule
  [conn schedule-row]
  (let [next-ts (-> schedule-row
                    :schedule/crontab
                    (helpers/next-run-at))
        query {:schedule/id (:schedule/id schedule-row)
               :schedule/run_at next-ts}]
    (log/debug "Update processed row in schedule table" query)
    (db/schedule-update! conn query)))

;; Entrypoint

(defn run
  "Schedule processing entrypoint"
  [_]
  (mount/start
   #'dienstplan.config/config
   #'dienstplan.db/db
   #'dienstplan.alerts/alerts)
  (process-events
   {:fn-get-schedules wrap-get-schedules
    :fn-shout-executable wrap-shout-executable
    :fn-process-executable wrap-process-executable
    :fn-update-schedule wrap-update-schedule}))

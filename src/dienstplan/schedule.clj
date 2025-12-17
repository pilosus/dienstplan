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
   [clojure.string :as s]
   [clojure.tools.logging :as log]
   [dienstplan.commands :as commands]
   [dienstplan.config :refer [config]]
   [dienstplan.db :as db]
   [dienstplan.helpers :as helpers]
   [mount.core :as mount]
   [next.jdbc :as jdbc])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

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

(defn start-system
  []
  (mount/start
   #'dienstplan.config/config
   #'dienstplan.db/db
   #'dienstplan.alerts/alerts))

(defn- process-schedules
  []
  (process-events
   {:fn-get-schedules wrap-get-schedules
    :fn-shout-executable wrap-shout-executable
    :fn-process-executable wrap-process-executable
    :fn-update-schedule wrap-update-schedule}))

(defn run
  "Schedule processing entrypoint. Used for one-time jobs"
  [_]
  (start-system)
  (process-schedules))

;; Daemon

(defn- create-scheduled-task
  "Create a runnable task with exception handling"
  []
  (fn []
    (try
      (process-schedules)
      (catch Exception e
        (log/error e "Schedule processing failed" (.getMessage e))))))

(defn start-executor
  "Start a scheduled executor that runs process-schedules with a fixed delay.
   Returns the executor for lifecycle management."
  ^ScheduledExecutorService []
  (let [delay-s (get-in config [:daemon :delay])
        executor (Executors/newSingleThreadScheduledExecutor)]
    (log/info "Starting schedule executor with delay" delay-s "seconds")
    (.scheduleWithFixedDelay executor
                             (create-scheduled-task)
                             0
                             delay-s
                             TimeUnit/SECONDS)
    executor))

(defn stop-executor
  "Gracefully stop the scheduled executor"
  [^ScheduledExecutorService executor]
  (when executor
    (log/info "Stopping schedule executor")
    (.shutdown executor)
    (when-not (.awaitTermination executor 30 TimeUnit/SECONDS)
      (log/warn "Executor did not terminate in time, forcing shutdown")
      (.shutdownNow executor))))

(defn daemonize
  "Schedule processing daemon. Used as a background worker.
   Blocks the main thread to keep the daemon running."
  [_]
  (start-system)
  (let [executor (start-executor)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(stop-executor executor)))
    ;; Block until executor terminates (keeps daemon alive)
    (.awaitTermination executor Long/MAX_VALUE TimeUnit/DAYS)))

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
   [dienstplan.commands :as commands]
   [dienstplan.db :as db]
   [dienstplan.helpers :as helpers]
   [mount.core :as mount]
   [next.jdbc :as jdbc]
   [org.pilosus.kairos :as kairos]))

(defn- next-run-ts
  "Given crontab line, return the next timestamp in JDBC compatible format"
  [schedule-row]
  (-> schedule-row
      :schedule/crontab
      (kairos/get-dt-seq)
      first
      .toInstant
      java.sql.Timestamp/from))

(defn- schedule-update-map
  [schedule-row]
  {:schedule/id (:schedule/id schedule-row)
   :schedule/run_at (next-run-ts schedule-row)})

(defn- exec-command-map
  "Get a request hashmap to run the bot command with"
  [schedule-row]
  {:params
   {:event
    ;; we need a placeholder in place of a bot mention to match the regex
    {:text (format "<@PLACEHOLDER> %s" (:schedule/executable schedule-row)),
     :ts (-> (helpers/now-ts-seconds)
             float
             str)
     :channel (:schedule/channel schedule-row)}}})

(defn process-rows
  "Iterate over rows from `schedule` table, process them, return number
  of processed rows"
  [conn rows fn-process-command fn-update-schedule]
  (loop [events (seq rows)
         processed 0]
    (if events
      (let [event (first events)
            command-map (exec-command-map event)
            query-map (schedule-update-map event)]
        (log/debug "Start processing event" event)

        (try
          (log/debug "Process scheduled command" command-map)
          (fn-process-command command-map)

          (log/debug "Update processed row in schedule table" query-map)
          (fn-update-schedule conn query-map)

          (catch Exception e
            (log/errorf e "Couldn't process row %s: %s" event (.getMessage e))
            (.rollback conn)))

        (log/debug "Event processed")
        (recur (next events) (inc processed)))
      processed)))

(defn process-events
  "Process scheduled events, return number of processed events"
  [fn-get-schedules fn-process-command fn-update-schedule]
  (jdbc/with-transaction [conn db/db]
    (log/info "Start processing scheduled events")
    (let [rows (fn-get-schedules conn (helpers/now-ts-sql))
          processed (process-rows conn rows fn-process-command fn-update-schedule)]
      (if (> processed 0)
        (do (log/infof "Processed %s event(s)" processed) processed)
        (do (log/info "No scheduled events found") 0)))))

(defn run
  "Schedule processing entrypoint"
  [_]
  (mount/start
   #'dienstplan.config/config
   #'dienstplan.db/db)
  (process-events db/schedules-get
                  commands/send-command-response!
                  db/schedule-update!))

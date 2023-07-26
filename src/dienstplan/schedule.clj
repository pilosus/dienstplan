(ns dienstplan.schedule
  (:require
   [dienstplan.db :as db]
   [clojure.tools.logging :as log]
   [org.pilosus.kairos :as kairos]
   [next.jdbc :as jdbc]))

;; TODO
;; fn-get-schedules - db/get-schedules
;; fn-update-schedule - db/update-schedule
;; fn-process-command - ???
(defn process-scheduled-commands
  [fn-get-schedules fn-process-command fn-update-schedule]
  (jdbc/with-transaction [conn db/db]
    (let [now (new java.sql.Timestamp (System/currentTimeMillis))
          rows (fn-get-schedules conn now)]
      (loop [events (seq rows)]
        (let [event (first events)
              next-run-at (-> event
                              :schedule/crontab
                              (kairos/get-dt-seq)
                              first
                              .toInstant
                              java.sql.Timestamp/from)]
          ;; If outter transaction rolls back, it doesn't affect nested one
          (jdbc/with-transaction [nested-trx db/db]
            (try
              (fn-process-command (:schedule/channel event)
                                  (:schedule/command event))
              (fn-update-schedule
               nested-trx
               {:schedule/id (:schedule/command event)
                :schedule/run_at next-run-at})
              (catch Exception e
                (log/error "Couldn't process command for event"
                           event
                           (.getMessage e))
                (.rollback nested-trx))))
          (when events
            (recur (next events))))))))

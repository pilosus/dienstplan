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

(ns dienstplan.fixtures-test
  "Reusable test fixtures"
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [dienstplan.slack :as slack]
   [dienstplan.verify :as verify]
   [hikari-cp.core :as cp]
   [mount.core :as mount :refer [defstate]]))

(defn fix-run-server
  [test]
  (log/info "[fix-run-server] start")
  (mount/start)
  (test)
  (mount/stop)
  (log/info "[fix-run-server] stop"))

(defn fix-mock-slack-api-request
  [test]
  (with-redefs
   [slack/slack-api-request (constantly {:ok? true :status 200 :date nil})
    verify/request-verified? (constantly true)]
    (test)))

(defstate db-test
  "Separate DB component with transaction rollback for tests"
  :start
  (let [db-opts (:db config)
        datasource (cp/make-datasource db-opts)]
    {:datasource datasource})
  :stop
  (-> db-test :datasource cp/close-datasource))

;; FIXME rollback won't work for test parametization via doseq
(defn fix-db-rollback
  [test]
  (log/info "[fix-db-rollback] start")
  (mount/start #'db-test)
  (jdbc/with-db-transaction [txn db-test]
    (jdbc/db-set-rollback-only! txn)
    (-> (mount/only [#'dienstplan.db/db])
        (mount/swap {#'dienstplan.db/db txn})
        (mount/start))
    (test)
    (mount/stop #'dienstplan.db/db))
  (mount/stop #'db-test)
  (log/info "[fix-db-rollback] stop"))

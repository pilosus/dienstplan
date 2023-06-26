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

(ns dienstplan.fixture
  "Reusable test fixtures"
  (:require
   [next.jdbc :as jdbc]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [dienstplan.slack :as slack]
   [dienstplan.verify :as verify]
   [dienstplan.db :as db]
   [next.jdbc.connection :as connection]
   [mount.core :as mount :refer [defstate]])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

;; Fake system components

(defstate db-test
  :start (connection/->pool HikariDataSource (:db config))
  :stop (-> db-test .close))

;; Fixtures

(defn fix-server-run
  "Run all system components"
  [test]
  (log/info "[fix-server-run] start")
  (mount/start)
  (test)
  (mount/stop)
  (log/info "[fix-server-run] stop"))

(defn fix-slack-api-request-mock
  "Slack API server mock"
  [test]
  (with-redefs
   [slack/slack-api-request (constantly {:ok? true :status 200 :date nil})
    verify/request-verified? (constantly true)]
    (test)))

(defn fix-db-rollback
  "Rollback nested transactions

  FIXME Rollback won't work for integration tests requiring full
  system run!

  Hypothesis: jetty serves requests in separate threads so that
  *nested-tx* and db component are overriden (see log/debug messages).
  The fixture works effectively only in cases where a connection is
  passed from a tests explicitly (see dienstplan.db-test ns)"
  [test]
  (log/info "[fix-db-rollback] start")
  (binding [next.jdbc.transaction/*nested-tx* :ignore]
    (jdbc/with-transaction [conn db-test {:auto-commit false}]
      ;; system component must be substituted with the test one
      ;; so that a single connection pool is used
      ;; and a transaction can be rolled back
      (-> (mount/only [#'dienstplan.db/db])
          (mount/swap {#'dienstplan.db/db conn})
          (mount/start))
      (test)
      (.rollback conn)))
  (log/info "[fix-db-rollback] stop"))

(def truncatable-tables
  ["rota" "mention"])

(defn fix-db-truncate
  "Truncate tables after the test run"
  [test]
  (log/info "[fix-db-truncate] start")
  (binding [next.jdbc.transaction/*nested-tx* :ignore]
    (jdbc/with-transaction [conn db-test]
      (-> (mount/only [#'dienstplan.db/db])
          (mount/swap {#'dienstplan.db/db conn})
          (mount/start))
      (test)
      (jdbc/execute!
       conn
       [(format
         "TRUNCATE TABLE %s CASCADE"
         (clojure.string/join ", " truncatable-tables))])))
  (log/info "[fix-db-truncate] stop"))

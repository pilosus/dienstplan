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

(ns dienstplan.spec
  (:gen-class)
  (:require
   [clojure.instant :refer [read-instant-date]]
   [clojure.spec.alpha :as s])
  (:import (java.sql Timestamp)))

;;;;;;;;;;;;;;;;;;;;;
;; Conform helpers ;;
;;;;;;;;;;;;;;;;;;;;;

(defmacro with-conformer
  [[bind] & body]
  `(s/conformer
    (fn [~bind]
      (try
        ~@body
        (catch Exception e#
          ::s/invalid)))))

(def ->bool
  (with-conformer [val]
    (boolean (Boolean/valueOf ^String val))))

(s/def ::->bool ->bool)

(def ->int
  (with-conformer [val]
    (Integer/parseInt val)))

(s/def ::->int ->int)
;;(s/conform ::->int "123")

(def ->uuid
  (with-conformer [val]
    (java.util.UUID/fromString val)))

(s/def ::->uuid
  (s/and
   string?
   not-empty
   ->uuid))
;;(s/conform ::->uuid "a2ab7fdb-b3f7-4aa6-bde5-7c37a0a39d71")

;; Helper functions

(defn varchar-length?
  [from to s]
  (let [length (count s)]
    (and (>= length from)
         (< length to))))

(def pg-timestamp-from
  ;; shall we support 4713 BC as PG does?
  (read-instant-date "0001-01-01T00:00:00.000"))

(def pg-timestamp-to
  ;; shall we support 294276 AD as PG does?
  (read-instant-date "3000-01-01T00:00:00.000"))

(defn instant-in?
  [^Timestamp from ^Timestamp to ^Timestamp instant]
  (and (.after instant from)
       (.before instant to)))

;;;;;;;;;;;;;;;;;;
;; Common specs ;;
;;;;;;;;;;;;;;;;;;

;; Basics

(s/def ::map map?)
(s/def ::nillable-map (s/nilable ::map))
(s/def ::kw keyword?)
(s/def ::str string?)
(s/def ::int integer?)
(s/def ::nillable-str (s/nilable ::str))
(s/def ::non-empty-str (s/and ::str not-empty))
(s/def ::boolean-str (s/and #{"true" "false"} ::->bool))
(s/def ::boolean boolean?)

;; Domain-related
(def timeout-max-ms (* 65535 1000))
(def pool-size-max 300)

(s/def ::ephemeral-port (s/and ::->int (s/int-in 1024 (inc 65535))))
(s/def ::pool-size (s/and ::->int (s/int-in 1 (inc pool-size-max))))
(s/def ::timeout (s/and ::->int (s/int-in 1000 (inc timeout-max-ms))))
(s/def ::lifetime (s/and ::->int (s/int-in 0 (inc timeout-max-ms))))

(s/def ::http-status-code (s/int-in 100 (inc 599)))
(s/def ::http-method #{:get :post :delete :put :patch :head})

(s/def ::db-serial (s/and ::int (s/int-in 1 (inc 2147483647))))
(s/def ::db-varchar-not-null-255
  (s/and
   ::str
   not-empty
   (partial varchar-length? 1 256)))

(s/def ::instant inst?)
(s/def ::db-timestamp
  (s/and
   ::instant
   (partial instant-in? pg-timestamp-from pg-timestamp-to)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application Configuration ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::application-config
  (s/keys :req-un [::application ::server ::daemon ::slack ::alerts ::db]))

;; Application

(s/def :application/name ::non-empty-str)
(s/def :application/version ::non-empty-str)
(s/def :application/env ::non-empty-str)
(s/def :application/debug ::boolean-str)

(s/def ::application
  (s/keys
   :req-un
   [:application/name
    :application/version
    :application/env
    :application/debug]))

;; Server

(def loglevels #{"TRACE" "DEBUG" "INFO" "WARN" "ERROR" "FATAL" "ALL" "OFF"})

(s/def :server/port ::ephemeral-port)
(s/def :server/loglevel loglevels)
(s/def :server/rootlevel loglevels)
(s/def :server/access-log ::boolean-str)
(s/def :server/block-thread ::boolean-str)

(s/def ::server
  (s/keys
   :req-un
   [:server/port
    :server/loglevel
    :server/rootlevel
    :server/access-log
    :server/block-thread]))

;; Daemon

(s/def :daemon/delay ::->int)

(s/def ::daemon
  (s/keys
   :req-un
   [:daemon/delay]))

;; Slack

(s/def :slack/token ::non-empty-str)
(s/def :slack/sign ::non-empty-str)

(s/def ::slack
  (s/keys
   :req-un
   [:slack/token
    :slack/sign]))

;; Alerts

(s/def :alerts/sentry ::str)

(s/def ::alerts
  (s/keys
   :req-un
   [:alerts/sentry]))

;; DB

(s/def :db/dbtype #{"postgres" "postgresql"})
(s/def :db/host ::non-empty-str)
(s/def :db/port ::ephemeral-port)
(s/def :db/dbname ::non-empty-str)
(s/def :db/username ::non-empty-str)
(s/def :db/password ::non-empty-str)
(s/def :db/maximumPoolSize ::pool-size)
(s/def :db/minimumIdle ::pool-size)
(s/def :db/connectionTimeout ::timeout)
(s/def :db/maxLifetime ::lifetime)
(s/def :db/keepaliveTime ::lifetime)

(s/def ::db
  (s/keys
   :req-un
   [:db/dbtype
    :db/host
    :db/port
    :db/dbname
    :db/username
    :db/password
    :db/maximumPoolSize
    :db/minimumIdle
    :db/connectionTimeout
    :db/maxLifetime
    :db/keepaliveTime]))

;;;;;;;;;;;;;;;;;;
;; Bot Commands ;;
;;;;;;;;;;;;;;;;;;

(s/def :bot-cmd-context/channel ::non-empty-str)
(s/def :bot-cmd-context/ts ::str)
(s/def :bot-cmd-context/team ::str)

(s/def :bot-cmd-common/context
  (s/keys
   :req-un
   [:bot-cmd-context/channel
    :bot-cmd-context/ts]
   :opt-un [:bot-cmd-context/team]))

(s/def :bot-cmd-common/command ::kw)

(s/def :bot-cmd-args/rotation-nil nil?)
(s/def :bot-cmd-args/rotation ::non-empty-str)
(s/def :bot-cmd-args/user ::non-empty-str)
(s/def :bot-cmd-args/description ::nillable-str)
(s/def :bot-cmd-args/users (s/nilable (s/+ ::non-empty-str)))
(s/def :bot-cmd-args-non-empty/users (s/+ ::non-empty-str))

(s/def :bot-cmd-default/args
  (s/keys
   :req-un
   [:bot-cmd-args/rotation]))

(s/def ::bot-cmd-default
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command
    :bot-cmd-default/args]))

(s/def ::bot-cmd-list
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command]))

(s/def :bot-cmd-create-or-update/args
  (s/keys
   :req-un
   [:bot-cmd-args/rotation
    :bot-cmd-args-non-empty/users
    :bot-cmd-args/description]))

(s/def :bot-cmd-assign/args
  (s/keys
   :req-un
   [:bot-cmd-args/rotation
    :bot-cmd-args/user]))

(s/def ::bot-cmd-create-or-update
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command
    :bot-cmd-create-or-update/args]))

(s/def :bot-schedule-args/subcommand #{"create" "delete" "list" "shout" "explain"})
(s/def :bot-schedule-args/executable ::nillable-str)
(s/def :bot-schedule-args/crontab ::nillable-str)

(s/def :bot-cmd-schedule/args
  (s/keys
   :req-un
   [:bot-schedule-args/subcommand
    :bot-schedule-args/executable
    :bot-schedule-args/crontab]))

(s/def ::bot-cmd-schedule
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command
    :bot-cmd-schedule/args]))

(s/def ::bot-cmd-assign
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command
    :bot-cmd-assign/args]))

(s/def :bot-cmd-help/args
  (s/keys
   :req-un
   [:bot-cmd-args/description]))

(s/def ::bot-cmd-help
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command]))

(s/def ::bot-cmd-list
  (s/keys
   :req-un
   [:bot-cmd-common/context
    :bot-cmd-common/command]))

;;;;;;;;;;;;;;;;;;
;; Bot Response ;;
;;;;;;;;;;;;;;;;;;

(s/def :bot-response/channel ::non-empty-str)
(s/def :bot-response/text ::non-empty-str)

(s/def ::bot-response
  (s/keys
   :req-un
   [:bot-response/channel
    :bot-response/text]))

;;;;;;;;;;;;;;;;;;;;
;; Function specs ;;
;;;;;;;;;;;;;;;;;;;;

(s/def :http-raw-response/body ::str)
(s/def :http-raw-response/status ::http-status-code)

(s/def ::http-raw-response
  (s/keys
   :req-un
   [:http-raw-response/status]
   :opt-un
   [:http-raw-response/body]))

(s/def :http-parsed-response/ok? ::boolean)
(s/def :http-parsed-response/status ::http-status-code)
(s/def :http-parsed-response/data (s/nilable ::map))

(s/def ::http-parsed-response
  (s/keys
   :req-un
   [:http-parsed-response/ok?]
   :opt-un
   [:http-parsed-response/status
    :http-parsed-response/data]))

(s/def :slack-api-request/method ::kw)
(s/def :slack-api-request/body ::str)
(s/def :slack-api-request/query-params ::map)

(s/def ::slack-api-request
  (s/keys
   :req-un
   [:slack-api-request/method]
   :opt-un
   [:slack-api-request/body
    :slack-api-request/query-params]))

;; http request for app mention event

(s/def :request-context/ts ::str)
(s/def :request-context/text ::str)
(s/def :request-context/channel ::non-empty-str)
(s/def :request-context/team ::nillable-str)

(s/def ::request-context-with-text
  (s/keys
   :req-un
   [:request-params-event/ts
    :request-params-event/text
    :request-params-event/channel]
   :opt-un
   [:request-params-event/team]))

(s/def ::request-context
  (s/keys
   :req-un
   [:request-params-event/ts
    :request-params-event/channel]
   :opt-un
   [:request-params-event/team]))

(s/def :request-params/event ::request-context-with-text)

(s/def :request/params
  (s/keys
   :req-un
   [:request-params/event]))

(s/def :request/raw-body ::str)
(s/def :request/headers (s/coll-of (s/tuple ::kw ::str)))

(s/def ::request
  (s/keys
   :opt-un
   [:request/params
    :request/raw-body
    :request/headers]))

(s/def ::raw-text ::nillable-str)

(s/def :command-parsed/command (s/nilable ::kw))
(s/def :command-parsed/rest ::nillable-str)

(s/def ::command-parsed
  (s/nilable
   (s/keys
    :req-un
    [:command-parsed/command
     :command-parsed/rest])))

(s/def :args-parsed/rotation ::non-empty-str)
(s/def :args-parsed/users (s/nilable (s/coll-of ::non-empty-str)))
(s/def :args-parsed/description ::str)

(s/def ::args-parsed
  (s/nilable
   (s/keys
    :opt-un
    [:args-parsed/rotation
     :args-parsed/users
     :args-parsed/description
     :args-parsed/user])))

;; TODO the spec is too complex,
;; command-map functions need to be simplified

(s/def :command-map/context ::request-context)
(s/def :command-map/command (s/nilable ::kw))
(s/def :command-map/args ::args-parsed)
(s/def :command-map/error ::str)

(s/def ::command-map
  (s/nilable
   (s/keys
    :req-un
    [:command-map/context]
    :opt-un
    [:command-map/command
     :command-map/args
     :command-map/error])))

(s/def ::command-response-text ::nillable-str)

(s/def :command-response/channel :request-context/channel)
(s/def :command-response/text ::command-response-text)

(s/def ::command-response
  (s/nilable
   (s/keys
    :req-un
    [:command-response/channel
     :command-response/text])))

;;;;;;;;;;;;;;;;;;;;;
;; Database Tables ;;
;;;;;;;;;;;;;;;;;;;;;

;; rota table

(s/def :rota/id ::db-serial)
(s/def :rota/channel ::db-varchar-not-null-255)
(s/def :rota/name ::db-varchar-not-null-255)
(s/def :rota/description ::nillable-str)
(s/def :rota/created_on ::instant) ;; ::db-timestamp
(s/def :rota/updated_on ::instant) ;; ::db-timestamp
(s/def :rota/meta ::nillable-map)

(s/def ::db-rota
  (s/keys
   :req
   [:rota/id
    :rota/channel
    :rota/name
    :rota/created_on
    :rota/updated_on]
   :opt
   [:rota/description
    :rota/meta]))

;; mention table

(s/def :mention/id ::db-serial)
(s/def :mention/rota_id ::db-serial)
(s/def :mention/name ::db-varchar-not-null-255)
(s/def :mention/duty ::boolean)

(s/def ::db-mention
  (s/keys
   :req
   [:mention/id
    :mention/rota_id
    :mention/name
    :mention/duty]))

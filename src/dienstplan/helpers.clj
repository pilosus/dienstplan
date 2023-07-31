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

(ns dienstplan.helpers
  "Common helper functions shared across namespaces"
  (:require [clojure.string :as string]
            [org.pilosus.kairos :as kairos])
  (:import (java.time ZonedDateTime)
           (java.sql Timestamp)))

(defn now-ts-sql
  "Get current timestamp in JDBC API compatible format"
  []
  (new java.sql.Timestamp (System/currentTimeMillis)))

(defn now-ts-seconds
  "Get current Unix timestamp in seconds"
  []
  (quot (System/currentTimeMillis) 1000))

(defn nilify
  [s]
  (if (string/blank? s) nil s))

(defn stringify
  [s]
  (or s ""))

(defn str-trim
  "Trim a string with extra three whitespace chars unsupported by Java regex"
  [s]
  (when (some? s)
    (-> s
        (string/replace #"^[\u00A0|\u2007|\u202F|\s|\.]*" "")
        (string/replace #"[\u00A0|\u2007|\u202F|\s|\.]*$" ""))))

(defn text-trim
  [s]
  (-> s
      (string/replace #"[,!?\-\.]*$" "")
      string/trim))

(defn next-run-at
  "Return java.sql.Timestamp for the next run for a given crontab string"
  ^Timestamp [crontab]
  (try (-> crontab
           (kairos/get-dt-seq)
           ^ZonedDateTime first
           .toInstant
           java.sql.Timestamp/from)
       (catch Exception _ nil)))

(defn cron-valid?
  "Return true if crontab is valid"
  [crontab]
  (-> crontab
      kairos/parse-cron
      some?))

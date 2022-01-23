;; Copyright (c) 2021-2022 Vitaly Samigullin and contributors. All rights reserved.
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

(ns dienstplan.verify
  "Verify request from slack
  https://api.slack.com/events/url_verification
  https://api.slack.com/authentication/verifying-requests-from-slack"
  (:gen-class)
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.mac :as mac]
   [clojure.spec.alpha :as s]
   [clojure.tools.logging :as log]
   [dienstplan.spec :as spec]))

(def VERSION "v0")
(def REPLAY_ATTACK_THRESHOLD_SECONDS (* 60 5))

(defn get-current-ts []
  (quot (System/currentTimeMillis) 1000))

(defn calculate-signature
  [sig-str sig-key]
  (let [hmac (-> (mac/hash sig-str {:key sig-key :alg :hmac+sha256})
                 (codecs/bytes->hex))
        signature (format "%s=%s" VERSION hmac)]
    signature))

(s/fdef request-verified?
  :args (s/cat :request ::spec/request :sig-key ::spec/str)
  :ret ::spec/boolean)

(defn request-verified?
  [request sig-key]
  (let [body (:raw-body request)
        headers (get request :headers)
        ts (Integer/parseInt (get headers :x-slack-request-timestamp))
        now (get-current-ts)
        replay-attack? (> (- now ts) REPLAY_ATTACK_THRESHOLD_SECONDS)
        recieved-sig (get headers :x-slack-signature)
        sig-str (format "%s:%s:%s" VERSION ts body)
        calculated-sig (calculate-signature sig-str sig-key)]
    (cond
      replay-attack?
      (do
        (log/error
         (format
          "Timestamp mistmatch. System time: %s, request time: %s"
          now ts))
        false)
      (not= recieved-sig calculated-sig)
      (do
        (log/error
         (format
          "Signature mismatch. Recieved signature: %s, calculated: %s"
          recieved-sig calculated-sig))
        false)
      :else
      (do
        (log/info "Request verified")
        true))))

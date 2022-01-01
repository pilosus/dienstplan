(ns dienstplan.verify
  "Verify request from slack
  https://api.slack.com/events/url_verification
  https://api.slack.com/authentication/verifying-requests-from-slack"
  (:gen-class)
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.mac :as mac]
   [clojure.tools.logging :as log]
))

(def VERSION "v0")
(def REPLAY_ATTACK_THRESHOLD_SECONDS (* 60 5))

(defn get-hmac
  [sig-str sig-key]
  (-> (mac/hash sig-str {:key sig-key :alg :hmac+sha256})
      (codecs/bytes->hex)))

(defn request-verified?
  [request sig-key]
  (let [body (:raw-body request)
        headers (get request :headers)
        ts (Integer/parseInt (get headers :x-slack-request-timestamp))
        now (quot (System/currentTimeMillis) 1000)
        replay-attack? (> (- now ts) REPLAY_ATTACK_THRESHOLD_SECONDS)
        recieved-sig (get headers :x-slack-signature)
        sig-str (format "%s:%s:%s" VERSION ts body)
        hmac (get-hmac sig-str sig-key)
        calculated-sig (format "%s=%s" VERSION hmac)]
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

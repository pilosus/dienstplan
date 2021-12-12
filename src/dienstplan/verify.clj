(ns dienstplan.verify
  "Verify request from slack
  https://api.slack.com/events/url_verification
  https://api.slack.com/authentication/verifying-requests-from-slack"
  (:gen-class)
  (:require
   [buddy.core.mac :as mac]
   [buddy.core.codecs :as codecs]
   [clojure.tools.logging :as log]))

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
        sig-str (str VERSION ":" ts ":" body)
        hmac (get-hmac sig-str sig-key)
        calculated-sig (str VERSION "=" hmac)]
    (cond
      replay-attack?
      (do
        (log/error
         (str
          "Timestamp mistmatch. "
          "System time: "
          now
          ", request time: "
          ts))
        false)
      (not= recieved-sig calculated-sig)
      (do
        (log/error
         (str "Signature mismatch. "
              "Recieved signature: "
              recieved-sig
              ", calculated: "
              calculated-sig))
        false)
      :else (do
              (log/info "Request verified")
              true))))

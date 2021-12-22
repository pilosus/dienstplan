(ns dienstplan.spec
  (:gen-class)
  (:require
   [clojure.spec.alpha :as s]))

;;;;;;;;;;;;;;;;;;;;;
;; Conform helpers ;;
;;;;;;;;;;;;;;;;;;;;;

(defn str->bool
  [val]
  (boolean (Boolean/valueOf val)))

;;;;;;;;;;;;;;;;;;
;; Common specs ;;
;;;;;;;;;;;;;;;;;;

;; Basics

(s/def ::str string?)
(s/def ::nillable-str (s/nilable ::str))
(s/def ::non-empty-str (s/and ::str not-empty))
(s/def ::boolean-str #{"true" "false"})

;; Domain-related

(s/def ::ephemeral-port (s/int-in 1024 (inc 65535)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application Configuration ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::application-config
  (s/keys :req-un [::application ::server ::slack ::alerts ::db]))

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

(s/def :server/port ::ephemeral-port)
(s/def :server/loglevel #{"DEBUG" "INFO" "WARN" "ERROR" "FATAL"})
(s/def :server/access-log ::boolean-str)

(s/def ::server
  (s/keys
   :req-un
   [:server/port
    :server/loglevel]))

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

(s/def :db/type #{"postgresql"})
(s/def :db/host ::non-empty-str)
(s/def :db/port ::ephemeral-port)
(s/def :db/name ::non-empty-str)
(s/def :db/user ::non-empty-str)
(s/def :db/password ::non-empty-str)

(s/def ::db
  (s/keys
   :req-un
   [:db/type
    :db/host
    :db/port
    :db/name
    :db/user
    :db/password]))

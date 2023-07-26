(ns dienstplan.helpers
  "Common helper functions shared across namespaces"
  (:require [clojure.string :as string]))

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

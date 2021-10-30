(ns dienstplan.logging
  (:gen-class)
  (:require
   [clojure.pprint]
   [clojure.tools.logging :as log]))


(defn ex-chain
  "Build exceptions chain from original one to the root"
  [^Exception e]
  (take-while some? (iterate ex-cause e)))

(defn ex-print
  "Pretty print a chain of exceptions"
  [^Throwable e]
  (let [indent " "]
    (doseq [e (ex-chain e)]
      (println (-> e class .getCanonicalName))
      (print indent)
      (println (ex-message e))
      (when-let [data (ex-data e)]
        (print indent)
        (clojure.pprint/pprint data)))))


(defn override-logging
  "Apply system-wide use of pretty print for chain of exceptions for log/error"
  []
  (alter-var-root
   (var clojure.tools.logging/log*)
   (fn [log*] ;; original log* function used in log/info, log/error, ... macros
     (fn [logger level throwable message]
       (if throwable
         (let [ex-out (with-out-str (ex-print throwable))
               message* (str message \newline ex-out)]
           (log* logger level nil message*))
         (log* logger level throwable message))))))

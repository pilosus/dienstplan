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
   ;; original log* function used in log/info, log/error, ... macros
   (fn [log*]
     (fn [logger level throwable message]
       (if throwable
         (let [ex-out (with-out-str (ex-print throwable))
               message* (str message \newline ex-out)]
           (log* logger level nil message*))
         (log* logger level throwable message))))))

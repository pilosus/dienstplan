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

(ns dienstplan.core
  (:gen-class)
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [dienstplan.server :as server]
   [dienstplan.db :as db]
   [dienstplan.schedule :as schedule]
   [mount.core :as mount]))

;; CLI opts

(def run-modes #{:server :migrate :rollback :schedule})

(def cli-options
  [["-m"
    "--mode MODE"
    "Run app in the mode specified"
    :default :server
    :parse-fn #(keyword (.toLowerCase %))
    :validate [#(contains? run-modes %) (format "App run modes: %s" run-modes)]]
   ["-h" "--help" "Print this help message"]])

(defn usage
  [options-summary]
  (->> ["dienstplan is a slack bot for duty rotations"
        ""
        "Usage: diesntplan [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn validate-args
  [args]
  (let [{:keys [options _ errors summary]} (parse-opts args cli-options)
        {:keys [help mode]} options]
    (cond
      help {:exit-message (usage summary) :ok? true}
      errors {:exit-message (error-msg errors)}
      mode {:mode mode}
      :else {:exit-message (usage summary) :ok? false})))

;; Entrypoint

(defn -main
  [& args]
  (let [{:keys [mode exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case mode
        :server (server/run nil)
        :migrate (db/migrate nil)
        :rollback (db/rollback nil)
        :schedule (schedule/run nil)))))

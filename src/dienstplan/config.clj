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

(ns dienstplan.config
  (:gen-class)
  (:require
   [mount.core :as mount :refer [defstate]]
   [clojure.tools.logging :as log]
   [yummy.config :refer [load-config]]
   [dienstplan.spec :as spec]))

(def CONFIG_PATH "resources/dienstplan/config.yaml")

(defn die-fn-repl
  "Load config die function to call in dev/repl mode upon failure"
  [e msg]
  (binding [*out* *err*]
    (println msg (ex-message e))))

(defn die-fn-prod
  "Load config die function to call in production upon failure"
  [e msg]
  (log/error e "Config error" msg)
  (System/exit 1))

(defstate config
  "Configuration map"
  :start
  (load-config {:path CONFIG_PATH
                :spec ::spec/application-config
                :die-fn die-fn-prod}))

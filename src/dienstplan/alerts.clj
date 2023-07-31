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

(ns dienstplan.alerts
  (:require
   [dienstplan.config :refer [config]]
   [mount.core :as mount :refer [defstate]]
   [sentry-clj.core :as sentry]))

(defstate alerts
  :start
  (let [dsn (get-in config [:alerts :sentry])
        debug (get-in config [:application :debug])
        env (get-in config [:application :env])
        app-name (get-in config [:application :name])
        version (get-in config [:application :version])
        release (format "%s:%s" app-name version)]
    (when (not debug)
      (sentry/init! dsn {:environment env :debug debug :release release})))
  :stop (sentry/close!))

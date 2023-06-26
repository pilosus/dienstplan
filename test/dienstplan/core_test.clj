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

(ns dienstplan.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.core :as core]))

(def params-validate-args
  [[["--help"]
    {:exit-message "dienstplan is a slack bot for duty rotations\n\nUsage: diesntplan [options]\n\nOptions:\n  -m, --mode MODE  :server  Run app in the mode specified\n  -h, --help                Print this help message" :ok? true}
    "Explicit help"]
   [["--whatever"]
    {:exit-message "The following errors occurred while parsing your command:\n\nUnknown option: \"--whatever\""}
    "Implicit help"]
   [["-m" "migrate"]
    {:mode :migrate}
    "Mode migrate short option"]
   [["--mode" "migrate"]
    {:mode :migrate}
    "Mode migrate full option"]])

(deftest test-validate-args
  (testing "Test validate-args"
    (doseq [[args expected description] params-validate-args]
      (testing description
        (is (= expected (core/validate-args args)))))))

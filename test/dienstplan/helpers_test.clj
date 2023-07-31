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

(ns dienstplan.helpers-test
  (:require
   [dienstplan.helpers :as h]
   [clojure.test :refer [deftest is testing]]))

(def params-str-trim
  [[nil nil "Nil"]
   ["text" "text" "Nothing to change"]
   ["text\u00A0" "text" "Right trim"]
   ["\u2007text" "text" "Left trim"]
   ["\u2007text\u202F" "text" "Full trim"]])

(deftest test-str-trim
  (testing "Test str-trim"
    (doseq [[s expected description] params-str-trim]
      (testing description
        (is (= expected (h/str-trim s)))))))

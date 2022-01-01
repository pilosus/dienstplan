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

(ns dienstplan.db-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.db :as db]))

(def params-rotate-users
  [[[] [] "Empty list"]
   [[{:duty false} {:duty false}]
    [{:duty false} {:duty false}]
    "No duty true, leave the list as it is"]
   [[{:id 12, :rota_id 10, :user "a", :duty true}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    [{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty true}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    "Rotate the first item"]
   [[{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty true}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    [{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty true}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    "Rotate item in the middle of the list"]
   [[{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty true}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    [{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty true}]
    "Rotate second to last item"]
   [[{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty true}]
    [{:id 12, :rota_id 10, :user "a", :duty true}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    "Rotate the last item"]
   [[{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty true}
     {:id 16, :rota_id 10, :user "d", :duty false}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty true}
     {:id 22, :rota_id 10, :user "g", :duty true}]
    [{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty true}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    "Auto-correct multiple duty true"]])

(deftest test-rotate-users
  (testing "Rotate list of user maps"
    (doseq [[users expected description] params-rotate-users]
      (testing description
        (is (= expected (db/rotate-users users)))))))

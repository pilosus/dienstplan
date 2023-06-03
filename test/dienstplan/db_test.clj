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
   [clojure.test :refer [deftest is testing use-fixtures]]
   [dienstplan.db :as db]
   [dienstplan.fixtures-test :as fix]))

(use-fixtures :once fix/fix-run-server)
(use-fixtures :each fix/fix-db-rollback)

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

(def params-assign-user
  [[[] "John" :user-not-found "Empty list"]
   [[{:id 1 :user "Ivan" :duty false}
     {:id 2 :user "Saqib" :duty false}]
    "John"
    :user-not-found
    "User not found"]
   [[{:id 12, :rota_id 10, :user "John", :duty true}
     {:id 14, :rota_id 10, :user "Ivan", :duty false}
     {:id 15, :rota_id 10, :user "Saqib", :duty false}]
    "John"
    [{:id 12, :rota_id 10, :user "John", :duty true}
     {:id 14, :rota_id 10, :user "Ivan", :duty false}
     {:id 15, :rota_id 10, :user "Saqib", :duty false}]
    "Already on duty"]
   [[{:id 12, :rota_id 10, :user "John", :duty true}
     {:id 14, :rota_id 10, :user "Ivan", :duty false}
     {:id 15, :rota_id 10, :user "Saqib", :duty false}]
    "Saqib"
    [{:id 12, :rota_id 10, :user "John", :duty false}
     {:id 14, :rota_id 10, :user "Ivan", :duty false}
     {:id 15, :rota_id 10, :user "Saqib", :duty true}]
    "New user"]])

(deftest test-assign-user
  (testing "Assign specific user"
    (doseq [[users name expected description] params-assign-user]
      (testing description
        (is (= expected (db/assign-user users name)))))))

(def params-get-duty-user-name
  [[[{:id 12, :rota_id 10, :user "a", :duty false}
     {:id 14, :rota_id 10, :user "b", :duty false}
     {:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty true}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 20, :rota_id 10, :user "f", :duty false}
     {:id 22, :rota_id 10, :user "g", :duty false}]
    "d"
    "Normal user list"]
   [[] nil "Empty user list"]
   [[{:id 15, :rota_id 10, :user "c", :duty false}
     {:id 16, :rota_id 10, :user "d", :duty true}
     {:id 17, :rota_id 10, :user "e", :duty false}
     {:id 18, :rota_id 10, :user "f", :duty true}]
    "d"
    "Get the first duty in the list wit multiple duty users"]])

(deftest test-get-duty-user-name
  (testing "Get duty user name"
    (doseq [[users expected description] params-get-duty-user-name]
      (testing description
        (is (= expected (db/get-duty-user-name users)))))))

(def rota-channel "my-channel")
(def rota-name "my-rota")
(def timestamp-1 (java.sql.Timestamp. 1495636054438))
(def timestamp-2 (java.sql.Timestamp. 1595636054438))

(def param-rota-1
  {:rota
   {:channel rota-channel
    :name rota-name
    :description "my-description-1"
    :created_on timestamp-1
    :updated_on timestamp-1}
   :mention [{:name "user-a", :duty false}
             {:name "user-b", :duty true}
             {:name "user-c", :duty false}]})

(def param-rota-2
  {:rota
   {:channel rota-channel
    :name rota-name
    :description "my-description-2"
    :created_on timestamp-2
    :updated_on timestamp-2}
   :mention [{:name "user-x", :duty true}
             {:name "user-y", :duty false}]})

(def params-rota-update!
  [[param-rota-1
    param-rota-2
    {:description "my-description-2"
     :duty "user-x"}
    "Description and mentions updated"]])

(deftest test-rota-update!
  (testing "Update rota"
    (doseq [[before after expected description] params-rota-update!]
      (testing description
        (db/rota-insert! before)
        (db/rota-update! after)
        (let [rota (first (db/duty-get rota-channel rota-name))]
          (is (= expected (dissoc rota :rota_id))))))))

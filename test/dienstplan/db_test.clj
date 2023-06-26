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
   [dienstplan.fixture :as fix]
   [next.jdbc :as jdbc]))

(use-fixtures :once fix/fix-server-run)
(use-fixtures :each fix/fix-db-rollback)

(def params-rotate-users
  [[[] [] "Empty list"]
   [[{:mention/duty false} {:mention/duty false}]
    [{:mention/duty false} {:mention/duty false}]
    "No duty true, leave the list as it is"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty true}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    [{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty true}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    "Rotate the first item"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty true}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    [{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty true}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    "Rotate item in the middle of the list"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty true}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    [{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty true}]
    "Rotate second to last item"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty true}]
    [{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty true}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    "Rotate the last item"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty true}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty false}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty true}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty true}]
    [{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty true}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    "Auto-correct multiple duty true"]])

(deftest test-rotate-users
  (testing "Rotate list of user maps"
    (doseq [[users expected description] params-rotate-users]
      (testing description
        (is (= expected (db/rotate-users users)))))))

(def params-assign-user
  [[[] "John" :user-not-found "Empty list"]
   [[{:mention/id 1 :mention/user "Ivan" :mention/duty false}
     {:mention/id 2 :mention/user "Saqib" :mention/duty false}]
    "John"
    :user-not-found
    "User not found"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "John", :mention/duty true}
     {:mention/id 14, :mention/rota_id 10, :mention/user "Ivan", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "Saqib", :mention/duty false}]
    "John"
    [{:mention/id 12, :mention/rota_id 10, :mention/user "John", :mention/duty true}
     {:mention/id 14, :mention/rota_id 10, :mention/user "Ivan", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "Saqib", :mention/duty false}]
    "Already on duty"]
   [[{:mention/id 12, :mention/rota_id 10, :mention/user "John", :mention/duty true}
     {:mention/id 14, :mention/rota_id 10, :mention/user "Ivan", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "Saqib", :mention/duty false}]
    "Saqib"
    [{:mention/id 12, :mention/rota_id 10, :mention/user "John", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "Ivan", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "Saqib", :mention/duty true}]
    "New user"]])

(deftest test-assign-user
  (testing "Assign specific user"
    (doseq [[users name expected description] params-assign-user]
      (testing description
        (is (= expected (db/assign-user users name)))))))

(def params-get-duty-user-name
  [[[{:mention/id 12, :mention/rota_id 10, :mention/user "a", :mention/duty false}
     {:mention/id 14, :mention/rota_id 10, :mention/user "b", :mention/duty false}
     {:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty true}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 20, :mention/rota_id 10, :mention/user "f", :mention/duty false}
     {:mention/id 22, :mention/rota_id 10, :mention/user "g", :mention/duty false}]
    "d"
    "Normal user list"]
   [[] nil "Empty user list"]
   [[{:mention/id 15, :mention/rota_id 10, :mention/user "c", :mention/duty false}
     {:mention/id 16, :mention/rota_id 10, :mention/user "d", :mention/duty true}
     {:mention/id 17, :mention/rota_id 10, :mention/user "e", :mention/duty false}
     {:mention/id 18, :mention/rota_id 10, :mention/user "f", :mention/duty true}]
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
    {:rota/description "my-description-2"
     :mention/duty "user-x"}
    "Description and mentions updated"]])

(deftest ^:integration test-rota-update!
  (testing "Update rota"
    (doseq [[before after expected description] params-rota-update!]
      (testing description
        (jdbc/with-transaction [conn db/db]
          (let [_ (db/rota-insert! before)
                _ (db/rota-update! after)
                rota (db/duty-get rota-channel rota-name)]
            (is (= expected (dissoc rota :rota/id)))))))))

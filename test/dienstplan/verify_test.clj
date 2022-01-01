(ns dienstplan.verify-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dienstplan.verify :as verify]))

(def RAW-BODY "request-body")

(def TS-NOW 1641037000)
(def TS-LATER-2-MIN 1641037120)
(def TS-LATER-10-MIN 1641037600)

(def SIG-1 "v0=sig1")
(def SIG-2 "v0=sig2")

(def params-calculate-signature
  [["str1" "key1" "v0=30ef07f9e56f54b91665a627db84472ef092c25c03391fe3739449b3cfaacd33" "string 1"]
   ["str2" "key2" "v0=8810694bc805c74bf3e0a70b5e57cb706d00e7f924c1fe178c8e6da3f3a41fb1" "string 2"]])

(deftest test-calculate-signature
  (testing "Test calculating HMAC signature"
    (doseq [[sig-str sig-key expected description] params-calculate-signature]
      (testing description
        (is (= expected (verify/calculate-signature sig-str sig-key)))))))

(def params-request-verified?
  [[{:raw-body RAW-BODY
     :headers {:x-slack-request-timestamp (str TS-NOW)
               :x-slack-signature SIG-1}}
    TS-NOW
    SIG-1
    true
    "Request verified"]
   [{:raw-body RAW-BODY
     :headers {:x-slack-request-timestamp (str TS-NOW)
               :x-slack-signature SIG-1}}
    TS-LATER-2-MIN
    SIG-1
    true
    "Time mismatched within normal interval. Request verified"]
   [{:raw-body RAW-BODY
     :headers {:x-slack-request-timestamp (str TS-NOW)
               :x-slack-signature SIG-1}}
    TS-LATER-10-MIN
    SIG-1
    false
    "Timestamp mismatch, possible replay attack"]
   [{:raw-body RAW-BODY
     :headers {:x-slack-request-timestamp (str TS-NOW)
               :x-slack-signature SIG-1}}
    TS-NOW
    SIG-2
    false
    "Signature mismatch"]])

(deftest test-request-verified?
  (testing "Test if request is verified"
    (doseq [[request
             current-ts
             calculated-sig
             expected
             description] params-request-verified?]
      (testing description
        (with-redefs
         [verify/calculate-signature (constantly calculated-sig)
          verify/get-current-ts (constantly current-ts)]
          (is
           (= expected
              (verify/request-verified? request "key1"))))))))

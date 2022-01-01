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

(ns file-pigeon.network-test
  (:require [clojure.test :refer [deftest is testing]]
            [file-pigeon.network :as network]))

(deftest test-get-lan-interfaces
  (testing "Returns a sequence of interface maps"
    (let [interfaces (network/get-lan-interfaces)]
      (is (sequential? interfaces))
      (doseq [iface interfaces]
        (is (contains? iface :interface))
        (is (contains? iface :ip))))))

(deftest test-get-primary-lan-ip
  (testing "Returns a non-blank IP string"
    (let [ip (network/get-primary-lan-ip)]
      (is (string? ip))
      (is (not (empty? ip)))
      ;; Matches basic IPv4 structure
      (is (re-matches #"\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}" ip)))))

(deftest test-format-server-urls
  (testing "Formats local and network URLs correctly"
    (let [urls (network/format-server-urls 8080)]
      (is (= "http://localhost:8080" (:local urls)))
      (is (vector? (:network urls)))
      (doseq [{:keys [url ip]} (:network urls)]
        (is (re-find #"8080" url))
        (is (re-matches #"\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}" ip))))))

(ns file-pigeon.qr-test
  (:require [clojure.test :refer [deftest is testing]]
            [file-pigeon.qr :as qr]))

(deftest test-terminal-qr
  (testing "Generates non-empty terminal QR string"
    (let [qr-str (qr/to-terminal-string "http://localhost:8080")]
      (is (string? qr-str))
      (is (> (count qr-str) 20))
      (is (re-find #"[█▀▄ ]" qr-str)))))

(deftest test-png-qr
  (testing "Generates valid PNG byte array"
    (let [bytes (qr/to-png-bytes "http://192.168.1.100:8080" 128)]
      (is (bytes? bytes))
      (is (> (alength bytes) 50))
      ;; PNG magic header bytes: 0x89 0x50 0x4E 0x47 (137, 80, 78, 71 in decimal signed byte is -119, 80, 78, 71)
      (is (= (byte -119) (aget bytes 0)))
      (is (= (byte 80) (aget bytes 1)))
      (is (= (byte 78) (aget bytes 2)))
      (is (= (byte 71) (aget bytes 3))))))

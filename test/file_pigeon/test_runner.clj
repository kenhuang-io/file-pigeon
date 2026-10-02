(ns file-pigeon.test-runner
  (:require [clojure.test :as t]
            [file-pigeon.network-test]
            [file-pigeon.storage-test]
            [file-pigeon.qr-test]
            [file-pigeon.routes-test]))

(defn -main [& _args]
  (let [{:keys [fail error]}
        (t/run-tests 'file-pigeon.network-test
                     'file-pigeon.storage-test
                     'file-pigeon.qr-test
                     'file-pigeon.routes-test)]
    (if (pos? (+ fail error))
      (System/exit 1)
      (System/exit 0))))

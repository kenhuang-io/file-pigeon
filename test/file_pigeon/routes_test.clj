(ns file-pigeon.routes-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [cheshire.core :as json]
            [file-pigeon.routes :as routes]
            [file-pigeon.middleware :refer [wrap-app-stack]]
            [file-pigeon.storage :as storage]
            [clojure.java.io :as io]))

(defn- create-temp-dir []
  (let [temp (java.nio.file.Files/createTempDirectory "pigeon-routes-test"
               (into-array java.nio.file.attribute.FileAttribute []))]
    (.toFile temp)))

(defn- delete-recursively [^java.io.File file]
  (when (.isDirectory file)
    (doseq [child (.listFiles file)]
      (delete-recursively child)))
  (.delete file))

(deftest test-routes
  (let [temp-dir (create-temp-dir)
        temp-path (.getAbsolutePath temp-dir)
        app (-> (routes/create-routes temp-path 8080)
                wrap-app-stack)]
    (try
      (testing "GET / returns HTML page"
        (let [res (app (mock/request :get "/"))]
          (is (= 200 (:status res)))
          (is (re-find #"text/html" (get-in res [:headers "Content-Type"])))
          (is (re-find #"File Pigeon" (:body res)))))

      (testing "GET /api/status returns JSON"
        (let [res (app (mock/request :get "/api/status"))
              data (json/parse-string (:body res) true)]
          (is (= 200 (:status res)))
          (is (= "ok" (:status data)))
          (is (= "file-pigeon" (:server data)))
          (is (= 8080 (:port data)))))

      (testing "GET /api/files returns directory structure"
        (let [res (app (mock/request :get "/api/files"))
              data (json/parse-string (:body res) true)]
          (is (= 200 (:status res)))
          (is (= "" (:current_path data)))
          (is (= [] (:items data)))
          (is (= 1 (count (:breadcrumbs data))))))

      (testing "Directory navigation and file listing flow"
        ;; Create a subfolder and files
        (let [sub (io/file temp-dir "photos")]
          (.mkdirs sub)
          (spit (io/file sub "sunset.png") "PNG fake bytes")

          ;; Check GET / with subfolder path
          (let [res (app (mock/request :get "/?path=photos"))]
            (is (= 200 (:status res)))
            (is (re-find #"photos" (:body res)))
            (is (re-find #"sunset.png" (:body res))))

          ;; Check GET /api/files with path=photos
          (let [res (app (mock/request :get "/api/files?path=photos"))
                data (json/parse-string (:body res) true)]
            (is (= 200 (:status res)))
            (is (= "photos" (:current_path data)))
            (is (= "" (:parent_path data)))
            (is (= 1 (count (:items data))))
            (is (= "sunset.png" (:name (first (:items data))))))

          ;; Download file via /download?path=photos/sunset.png
          (let [dl-res (app (mock/request :get "/download?path=photos/sunset.png"))]
            (is (= 200 (:status dl-res)))
            (is (= "image/png" (get-in dl-res [:headers "Content-Type"])))
            (is (= "PNG fake bytes" (slurp (:body dl-res)))))))

      (testing "File upload, download, and delete flow"
        ;; 1. Upload
        (let [temp-upload (java.io.File/createTempFile "test-upload" ".txt")]
          (spit temp-upload "Clojure Pigeon Content")
          (let [upload-req (-> (mock/request :post "/api/upload")
                               (assoc :params {"file" {:filename "test-note.txt"
                                                       :tempfile temp-upload
                                                       :size 22}}))
                upload-res (app upload-req)
                upload-data (json/parse-string (:body upload-res) true)]
            (is (= 200 (:status upload-res)))
            (is (true? (:success upload-data)))
            (is (= 1 (:count upload-data)))))

        ;; 2. List files
        (let [res (app (mock/request :get "/api/files"))
              data (json/parse-string (:body res) true)]
          (is (some #(= "test-note.txt" (:name %)) (:items data))))

        ;; 3. Download
        (let [res (app (mock/request :get "/files/test-note.txt"))]
          (is (= 200 (:status res)))
          (is (= "Clojure Pigeon Content" (slurp (:body res)))))

        ;; 4. Delete
        (let [del-res (app (mock/request :delete "/api/files?path=test-note.txt"))
              del-data (json/parse-string (:body del-res) true)]
          (is (= 200 (:status del-res)))
          (is (true? (:success del-data))))

        ;; 5. Verify deleted
        (let [res (app (mock/request :get "/api/files"))
              data (json/parse-string (:body res) true)]
          (is (not (some #(= "test-note.txt" (:name %)) (:items data))))))

      (testing "GET /qr returns PNG image"
        (let [res (app (mock/request :get "/qr"))]
          (is (= 200 (:status res)))
          (is (= "image/png" (get-in res [:headers "Content-Type"])))))

      (testing "GET /qr/text returns LAN URL text"
        (let [res (app (mock/request :get "/qr/text"))]
          (is (= 200 (:status res)))
          (is (= "text/plain; charset=utf-8" (get-in res [:headers "Content-Type"])))))

      (testing "404 handler"
        (let [res (app (mock/request :get "/non-existent-path"))]
          (is (= 404 (:status res)))))

      (finally
        (delete-recursively temp-dir)))))

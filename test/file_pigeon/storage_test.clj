(ns file-pigeon.storage-test
  (:require [clojure.test :refer [deftest is testing]]
            [file-pigeon.storage :as storage]
            [clojure.java.io :as io]))

(defn- create-temp-dir []
  (let [temp (java.nio.file.Files/createTempDirectory "pigeon-test"
               (into-array java.nio.file.attribute.FileAttribute []))]
    (.toFile temp)))

(defn- delete-recursively [^java.io.File file]
  (when (.isDirectory file)
    (doseq [child (.listFiles file)]
      (delete-recursively child)))
  (.delete file))

(deftest test-sanitize-filename
  (testing "Sanitizes path traversal attempts"
    (is (= "passwd" (storage/sanitize-filename "../../etc/passwd")))
    (is (= "malicious.sh" (storage/sanitize-filename "foo/bar/../../malicious.sh")))
    (is (= "file.txt" (storage/sanitize-filename "file.txt")))
    (is (= "unnamed-file" (storage/sanitize-filename "")))
    (is (= "unnamed-file" (storage/sanitize-filename "..")))
    (is (= "unnamed-file" (storage/sanitize-filename "...")))))

(deftest test-format-file-size
  (testing "Formats byte sizes to human readable strings"
    (is (= "500 B" (storage/format-file-size 500)))
    (is (= "1.5 KB" (storage/format-file-size 1536)))
    (is (= "2.0 MB" (storage/format-file-size (* 2 1024 1024))))
    (is (= "1.50 GB" (storage/format-file-size (* 1536 1024 1024))))))

(deftest test-breadcrumbs-and-parent
  (testing "Breadcrumbs navigation generation"
    (let [crumbs (storage/make-breadcrumbs "workspace/side-projects/file-pigeon")]
      (is (= 4 (count crumbs)))
      (is (= "Home" (:name (first crumbs))))
      (is (= "" (:path (first crumbs))))
      (is (= "workspace" (:name (second crumbs))))
      (is (= "workspace" (:path (second crumbs))))
      (is (= "file-pigeon" (:name (last crumbs))))
      (is (= "workspace/side-projects/file-pigeon" (:path (last crumbs)))))

    (let [root-crumbs (storage/make-breadcrumbs "")]
      (is (= 1 (count root-crumbs)))
      (is (= "Home" (:name (first root-crumbs))))))

  (testing "Parent path calculation"
    (is (nil? (storage/get-parent-path "")))
    (is (nil? (storage/get-parent-path nil)))
    (is (= "" (storage/get-parent-path "workspace")))
    (is (= "workspace" (storage/get-parent-path "workspace/side-projects")))
    (is (= "workspace/side-projects" (storage/get-parent-path "workspace/side-projects/file-pigeon")))))

(deftest test-directory-listing-and-traversal-prevention
  (let [temp-dir (create-temp-dir)
        temp-path (.getAbsolutePath temp-dir)
        sub-dir (io/file temp-dir "docs")
        nested-dir (io/file sub-dir "nested")]
    (try
      (.mkdirs nested-dir)
      (spit (io/file temp-dir "root.txt") "root file")
      (spit (io/file sub-dir "doc.pdf") "pdf content")

      (testing "Listing root directory items"
        (let [items (storage/list-dir-contents temp-path "")]
          (is (= 2 (count items)))
          ;; Directory docs should be listed first
          (let [first-item (first items)]
            (is (true? (:is-dir first-item)))
            (is (= "docs" (:name first-item)))
            (is (= :folder (:category first-item)))
            (is (= "/?path=docs" (:browse-url first-item))))
          ;; File root.txt second
          (let [second-item (second items)]
            (is (false? (:is-dir second-item)))
            (is (= "root.txt" (:name second-item)))
            (is (= "/download?path=root.txt" (:download-url second-item))))))

      (testing "Listing subdirectory items"
        (let [sub-items (storage/list-dir-contents temp-path "docs")]
          (is (= 2 (count sub-items)))
          (is (= "nested" (:name (first sub-items))))
          (is (true? (:is-dir (first sub-items))))
          (is (= "/?path=docs%2Fnested" (:browse-url (first sub-items))))
          (is (= "doc.pdf" (:name (second sub-items))))
          (is (false? (:is-dir (second sub-items))))))

      (testing "Directory traversal resolution clamps safely to root"
        (let [resolved (storage/resolve-dir temp-path "../../etc")]
          (is (= (.getCanonicalPath temp-dir) (.getCanonicalPath resolved))))
        (let [items (storage/list-dir-contents temp-path "../../etc")]
          ;; Should show root items instead of escaping
          (is (= 2 (count items)))))

      (finally
        (delete-recursively temp-dir)))))

(deftest test-storage-crud
  (let [temp-dir (create-temp-dir)
        temp-path (.getAbsolutePath temp-dir)]
    (try
      (testing "Storage directory initialization and empty listing"
        (is (.exists (storage/ensure-storage-dir! temp-path)))
        (is (= [] (storage/list-files temp-path))))

      (testing "Saving uploaded files"
        (let [sample-content "Hello File Pigeon!"
              temp-upload (java.io.File/createTempFile "upload" ".txt")]
          (spit temp-upload sample-content)
          (let [saved (storage/save-uploaded-file! temp-path
                                                 {:filename "hello.txt"
                                                  :tempfile temp-upload
                                                  :size (count sample-content)})]
            (is (= "hello.txt" (:name saved)))
            (is (= (count sample-content) (:size saved)))
            (is (= :text (:category saved)))
            (is (= 1 (count (storage/list-files temp-path))))

            ;; Safe file retrieval
            (let [file (storage/get-file temp-path "hello.txt")]
              (is (some? file))
              (is (= sample-content (slurp file))))

            ;; Directory traversal prevention in retrieval
            (is (nil? (storage/get-file temp-path "../../../etc/passwd")))

            ;; File stats
            (let [stats (storage/get-storage-stats temp-path)]
              (is (= 1 (:file-count stats)))
              (is (= (count sample-content) (:total-bytes stats))))

            ;; File deletion
            (is (true? (storage/delete-file! temp-path "hello.txt")))
            (is (false? (storage/delete-file! temp-path "hello.txt")))
            (is (= 0 (count (storage/list-files temp-path)))))))
      (finally
        (delete-recursively temp-dir)))))

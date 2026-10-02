(ns file-pigeon.storage
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]
           [java.net URLEncoder]
           [java.time Instant]
           [java.time.format DateTimeFormatter]))

(def default-home-dir (System/getProperty "user.home"))

(defn ensure-storage-dir!
  "Ensures that the storage directory exists."
  [dir-path]
  (let [dir (io/file (or dir-path default-home-dir))]
    (when-not (.exists dir)
      (.mkdirs dir))
    dir))

(defn sanitize-filename
  "Strips path characters and parent references to prevent directory traversal attacks."
  [filename]
  (if (or (str/blank? filename)
          (re-matches #"^\.+$" (str/trim filename)))
    "unnamed-file"
    (let [base-name (-> (File. filename)
                        .getName
                        (str/replace #"[/\\]" "")
                        (str/replace #"\.\.+" "."))]
      (if (or (str/blank? base-name)
              (re-matches #"^\.+$" base-name))
        "unnamed-file"
        base-name))))

(defn format-file-size
  "Formats byte length into human-readable representation."
  [bytes]
  (cond
    (< bytes 1024) (format "%d B" bytes)
    (< bytes (* 1024 1024)) (format "%.1f KB" (/ (double bytes) 1024.0))
    (< bytes (* 1024 1024 1024)) (format "%.1f MB" (/ (double bytes) (* 1024.0 1024.0)))
    :else (format "%.2f GB" (/ (double bytes) (* 1024.0 1024.0 1024.0)))))

(defn get-file-category
  "Returns a keyword representing the general category of the file based on extension."
  [filename]
  (let [ext (some-> (re-find #"\.([a-zA-Z0-9]+)$" (str/lower-case filename))
                    second)]
    (case ext
      ("png" "jpg" "jpeg" "gif" "svg" "webp" "bmp" "ico") :image
      ("mp4" "mkv" "mov" "avi" "webm") :video
      ("mp3" "wav" "ogg" "flac" "m4a") :audio
      ("pdf") :pdf
      ("zip" "tar" "gz" "tgz" "7z" "rar" "bz2") :archive
      ("clj" "cljs" "edn" "js" "ts" "json" "html" "css" "py" "go" "rs" "java" "c" "cpp" "sh") :code
      ("doc" "docx" "xls" "xlsx" "ppt" "pptx" "odt" "ods") :document
      ("txt" "md" "org" "log" "csv" "tsv") :text
      :file)))

(defn url-encode [s]
  (some-> s
          (URLEncoder/encode "UTF-8")
          (str/replace "+" "%20")))

(defn get-relative-path
  "Computes relative path of target-file from base-dir. Returns empty string if target is base-dir."
  [base-dir target-file]
  (let [base (io/file (or base-dir default-home-dir))
        target (io/file target-file)
        base-path (.getCanonicalPath base)
        target-path (.getCanonicalPath target)]
    (if (= base-path target-path)
      ""
      (let [rel (subs target-path (count base-path))]
        (-> (if (.startsWith rel File/separator)
              (subs rel 1)
              rel)
            (str/replace "\\" "/"))))))

(defn resolve-dir
  "Resolves a directory relative to base-dir. Enforces that the target cannot escape base-dir."
  [base-dir requested-path]
  (let [base (ensure-storage-dir! (or base-dir default-home-dir))
        base-canonical (.getCanonicalPath base)
        target (if (str/blank? requested-path)
                 base
                 (let [f (io/file requested-path)]
                   (if (.isAbsolute f)
                     f
                     (io/file base requested-path))))]
    (if (and (.exists target)
             (.isDirectory target)
             (.startsWith (.getCanonicalPath target) base-canonical))
      target
      base)))

(defn make-breadcrumbs
  "Returns a list of breadcrumb maps {:name ... :path ...} for navigation."
  [rel-path]
  (if (str/blank? rel-path)
    [{:name "Home" :path ""}]
    (let [parts (str/split (str/replace rel-path "\\" "/") #"/")]
      (loop [remaining parts
             accum []
             result [{:name "Home" :path ""}]]
        (if (empty? remaining)
          result
          (let [cur (first remaining)
                new-accum (conj accum cur)
                new-path (str/join "/" new-accum)]
            (recur (rest remaining)
                   new-accum
                   (conj result {:name cur :path new-path}))))))))

(defn get-parent-path
  "Returns the relative path of the parent directory, or nil if already at root."
  [rel-path]
  (when-not (str/blank? rel-path)
    (let [normalized (str/replace rel-path "\\" "/")
          idx (str/last-index-of normalized "/")]
      (if (and idx (pos? idx))
        (subs normalized 0 idx)
        ""))))

(defn- item->record [base-dir ^File f]
  (let [name (.getName f)
        is-dir (.isDirectory f)
        len (if is-dir 0 (.length f))
        last-mod (.lastModified f)
        iso-time (try (.format (DateTimeFormatter/ISO_INSTANT) (Instant/ofEpochMilli last-mod))
                      (catch Exception _ ""))
        rel-path (get-relative-path base-dir f)
        encoded-rel (url-encode rel-path)]
    {:name name
     :is-dir is-dir
     :size len
     :size-formatted (if is-dir "-" (format-file-size len))
     :last-modified iso-time
     :category (if is-dir :folder (get-file-category name))
     :relative-path rel-path
     :browse-url (when is-dir (str "/?path=" encoded-rel))
     :download-url (when-not is-dir (str "/download?path=" encoded-rel))}))

(defn list-dir-contents
  "Lists all files and directories in the directory specified by requested-path.
   Directories are sorted first, followed by files."
  [base-dir requested-path]
  (let [base (ensure-storage-dir! (or base-dir default-home-dir))
        target (resolve-dir base requested-path)
        all-items (try (or (.listFiles ^File target) (into-array File []))
                       (catch Exception _ (into-array File [])))
        visible-items (filter (fn [^File f]
                                (and (not (.isHidden f))
                                     (not (.startsWith (.getName f) "."))))
                              all-items)
        records (map #(item->record base %) visible-items)
        dirs (->> records
                  (filter :is-dir)
                  (sort-by (comp str/lower-case :name)))
        files (->> records
                   (remove :is-dir)
                   (sort-by (comp str/lower-case :name)))]
    (vec (concat dirs files))))

(defn list-files
  "Lists all files in the storage directory, sorted by last modified (newest first).
   Preserved for backwards compatibility."
  [dir-path]
  (let [dir (ensure-storage-dir! dir-path)
        files (filter (fn [^File f] (and (.isFile f) (not (.isHidden f))))
                      (.listFiles dir))]
    (->> files
         (map (fn [^File f]
                (let [name (.getName f)
                      len (.length f)
                      last-mod (.lastModified f)
                      iso-time (try (.format (DateTimeFormatter/ISO_INSTANT) (Instant/ofEpochMilli last-mod))
                                    (catch Exception _ ""))]
                  {:name name
                   :size len
                   :size-formatted (format-file-size len)
                   :last-modified iso-time
                   :category (get-file-category name)
                   :download-url (str "/files/" (url-encode name))})))
         (sort-by :last-modified #(compare %2 %1))
         vec)))

(defn- get-unique-target-file
  "Generates a unique File object in target-dir to prevent accidental overwriting."
  [^File dir filename]
  (let [dot-idx (str/last-index-of filename ".")
        [base ext] (if (and dot-idx (pos? dot-idx))
                     [(subs filename 0 dot-idx) (subs filename dot-idx)]
                     [filename ""])
        candidate (File. dir filename)]
    (if-not (.exists candidate)
      candidate
      (loop [counter 1]
        (let [next-candidate (File. dir (format "%s (%d)%s" base counter ext))]
          (if-not (.exists next-candidate)
            next-candidate
            (recur (inc counter))))))))

(defn save-uploaded-file!
  "Saves an uploaded file map (from Ring multipart-params) into target-dir.
   Returns the saved file record."
  [target-dir {:keys [filename tempfile size]}]
  (let [dir (ensure-storage-dir! target-dir)
        clean-name (sanitize-filename filename)
        target-file (get-unique-target-file dir clean-name)]
    (io/copy tempfile target-file)
    (item->record dir target-file)))

(defn get-file
  "Safely retrieves a File object inside base-dir by relative or absolute path.
   Returns nil if the file does not exist, is a directory, or tries to escape base-dir."
  [base-dir requested-path]
  (when-not (str/blank? requested-path)
    (let [base (ensure-storage-dir! (or base-dir default-home-dir))
          base-canonical (.getCanonicalPath base)
          clean-path (str/replace requested-path "\\" "/")
          target (let [f (io/file clean-path)]
                   (if (.isAbsolute f)
                     f
                     (io/file base clean-path)))]
      (when (and (.exists target)
                 (.isFile target)
                 (.startsWith (.getCanonicalPath target) base-canonical))
        target))))

(defn delete-file!
  "Safely deletes a file inside base-dir by path or filename.
   Returns true if deleted, false otherwise."
  [base-dir path-or-name]
  (if-let [file (get-file base-dir path-or-name)]
    (boolean (.delete file))
    false))

(defn get-storage-stats
  "Returns storage statistics for dir-path."
  [dir-path]
  (let [dir (ensure-storage-dir! dir-path)
        items (try (or (.listFiles ^File dir) (into-array File []))
                   (catch Exception _ (into-array File [])))
        files (filter (fn [^File f] (and (.isFile f) (not (.isHidden f)))) items)
        dirs (filter (fn [^File f] (and (.isDirectory f) (not (.isHidden f)))) items)
        total-bytes (reduce + 0 (map #(.length ^File %) files))
        usable-space (.getUsableSpace dir)]
    {:file-count (count files)
     :dir-count (count dirs)
     :total-bytes total-bytes
     :total-formatted (format-file-size total-bytes)
     :usable-space-bytes usable-space
     :usable-space-formatted (format-file-size usable-space)
     :directory (.getCanonicalPath dir)}))

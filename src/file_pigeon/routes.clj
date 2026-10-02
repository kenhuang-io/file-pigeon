(ns file-pigeon.routes
  (:require [compojure.core :refer [defroutes GET POST DELETE OPTIONS ANY routes context]]
            [compojure.route :as route]
            [ring.util.response :as res]
            [ring.util.mime-type :as mime]
            [file-pigeon.storage :as storage]
            [file-pigeon.network :as network]
            [file-pigeon.qr :as qr]
            [file-pigeon.views :as views]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io ByteArrayInputStream File]))

(defn- extract-files
  "Extracts uploaded file maps from request params."
  [params]
  (let [items (flatten (vals (select-keys params ["files" "file" :files :file])))]
    (filter #(and (map? %) (contains? % :tempfile)) items)))

(defn- serve-file-response [^File file download?]
  (let [mime-type (or (mime/ext-mime-type (.getName file))
                      "application/octet-stream")
        disposition (if download?
                      (format "attachment; filename=\"%s\"" (.getName file))
                      (format "inline; filename=\"%s\"" (.getName file)))]
    (-> (res/response file)
        (res/content-type mime-type)
        (res/header "Content-Length" (str (.length file)))
        (res/header "Content-Disposition" disposition)
        (res/header "Accept-Ranges" "bytes"))))

(defn- create-api-routes [storage-dir port start-time]
  (routes
   (GET "/status" []
     (let [uptime-ms (- (System/currentTimeMillis) start-time)
           server-urls (network/format-server-urls port)
           stats (storage/get-storage-stats storage-dir)]
       {:status 200
        :headers {"Content-Type" "application/json; charset=utf-8"}
        :body {:status "ok"
               :server "file-pigeon"
               :version "0.1.0"
               :port port
               :uptime-seconds (quot uptime-ms 1000)
               :urls server-urls
               :storage stats}}))

   (GET "/files" [path :as req]
     (let [target-dir (storage/resolve-dir storage-dir path)
           rel-path (storage/get-relative-path storage-dir target-dir)
           items (storage/list-dir-contents storage-dir rel-path)
           breadcrumbs (storage/make-breadcrumbs rel-path)
           parent-path (storage/get-parent-path rel-path)]
       {:status 200
        :headers {"Content-Type" "application/json; charset=utf-8"}
        :body {:current_path rel-path
               :absolute_path (.getCanonicalPath target-dir)
               :parent_path parent-path
               :breadcrumbs breadcrumbs
               :items items}}))

   (POST "/upload" [ :as req]
     (let [params (:params req)
           target-path (or (get params :path) (get params "path"))
           target-dir (storage/resolve-dir storage-dir target-path)
           files (extract-files params)]
       (if (empty? files)
         {:status 400
          :headers {"Content-Type" "application/json; charset=utf-8"}
          :body {:success false :message "No files provided in upload request"}}
         (let [saved (mapv #(storage/save-uploaded-file! target-dir %) files)]
           {:status 200
            :headers {"Content-Type" "application/json; charset=utf-8"}
            :body {:success true
                   :count (count saved)
                   :uploaded saved
                   :directory (.getCanonicalPath target-dir)
                   :message (format "%d file(s) uploaded successfully" (count saved))}}))))

   (DELETE "/files" [path :as req]
     (let [target-path (or path (get-in req [:params "path"]))]
       (if (and target-path (storage/delete-file! storage-dir target-path))
         {:status 200
          :headers {"Content-Type" "application/json; charset=utf-8"}
          :body {:success true
                 :path target-path
                 :message (format "File '%s' deleted successfully" target-path)}}
         {:status 404
          :headers {"Content-Type" "application/json; charset=utf-8"}
          :body {:success false
                 :path target-path
                 :message (format "File '%s' not found" target-path)}})))

   (DELETE "/files/:filename" [filename]
     (let [decoded-name (java.net.URLDecoder/decode filename "UTF-8")]
       (if (storage/delete-file! storage-dir decoded-name)
         {:status 200
          :headers {"Content-Type" "application/json; charset=utf-8"}
          :body {:success true
                 :filename decoded-name
                 :message (format "File '%s' deleted successfully" decoded-name)}}
         {:status 404
          :headers {"Content-Type" "application/json; charset=utf-8"}
          :body {:success false
                 :filename decoded-name
                 :message (format "File '%s' not found" decoded-name)}})))))

(defn create-routes
  "Creates the Compojure routes tree configured for the given storage directory and port."
  [storage-dir port]
  (let [start-time (System/currentTimeMillis)]
    (routes
     ;; Web UI Root: displays files & directories, supports ?path=...
     (GET "/" [path :as req]
       (let [target-dir (storage/resolve-dir storage-dir path)
             rel-path (storage/get-relative-path storage-dir target-dir)
             items (storage/list-dir-contents storage-dir rel-path)
             breadcrumbs (storage/make-breadcrumbs rel-path)
             parent-path (storage/get-parent-path rel-path)
             stats (storage/get-storage-stats target-dir)
             server-urls (network/format-server-urls port)]
         (-> (views/index-page {:server-urls server-urls
                                :base-dir storage-dir
                                :current-path rel-path
                                :current-abs-path (.getCanonicalPath target-dir)
                                :breadcrumbs breadcrumbs
                                :parent-path parent-path
                                :items items
                                :storage-stats stats
                                :port port})
             res/response
             (res/content-type "text/html; charset=utf-8"))))

     ;; REST API Context
     (context "/api" []
       (create-api-routes storage-dir port start-time))

     ;; Direct Download by relative path (?path=...)
     (GET "/download" [path :as req]
       (let [download? (get (:params req) "download" true)]
         (if-let [^File file (storage/get-file storage-dir path)]
           (serve-file-response file download?)
           {:status 404
            :headers {"Content-Type" "text/plain; charset=utf-8"}
            :body (format "File not found: %s" path)})))

     ;; File Download by filename or path (/files/:filename)
     (GET "/files/:filename" [filename :as req]
       (let [decoded-name (java.net.URLDecoder/decode filename "UTF-8")
             path-param (get (:params req) "path")
             target-path (or path-param decoded-name)]
         (if-let [^File file (storage/get-file storage-dir target-path)]
           (let [download? (get (:params req) "download")]
             (serve-file-response file download?))
           {:status 404
            :headers {"Content-Type" "text/plain; charset=utf-8"}
            :body (format "File not found: %s" target-path)})))

     ;; QR Code Image Endpoint (PNG)
     (GET "/qr" []
       (let [server-urls (network/format-server-urls port)
             lan-url (or (-> server-urls :network first :url)
                         (:local server-urls))
             qr-bytes (qr/to-png-bytes lan-url 300)]
         (-> (ByteArrayInputStream. qr-bytes)
             res/response
             (res/content-type "image/png")
             (res/header "Cache-Control" "no-cache"))))

     ;; QR Code LAN URL as Plain Text
     (GET "/qr/text" []
       (let [server-urls (network/format-server-urls port)
             lan-url (or (-> server-urls :network first :url)
                         (:local server-urls))]
         (-> (res/response lan-url)
             (res/content-type "text/plain; charset=utf-8"))))

     ;; Static Resources
     (route/resources "/")

     ;; 404 Handler
     (ANY "*" req
       (let [accept (get-in req [:headers "accept"] "")]
         (if (str/includes? (or accept "") "application/json")
           {:status 404
            :headers {"Content-Type" "application/json; charset=utf-8"}
            :body {:error "Not Found" :uri (:uri req)}}
           {:status 404
            :headers {"Content-Type" "text/html; charset=utf-8"}
            :body (str "<h1>404 Not Found</h1><p>The requested URL " (:uri req) " was not found.</p>")}))))))

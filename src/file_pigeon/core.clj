(ns file-pigeon.core
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [file-pigeon.server :as server]
            [file-pigeon.network :as network]
            [file-pigeon.qr :as qr]
            [file-pigeon.storage :as storage]))

(def cli-options
  [["-p" "--port PORT" "Port to listen on"
    :default (if-let [env-p (System/getenv "PORT")]
               (Integer/parseInt env-p)
               8080)
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 65536) "Must be an integer between 1 and 65535"]]
   ["-H" "--host HOST" "Host interface to bind on (0.0.0.0 for LAN)"
    :default (or (System/getenv "HOST") "0.0.0.0")]
   ["-d" "--dir DIR" "Directory for browsing and sharing files"
    :id :storage-dir
    :default (or (System/getenv "SHARED_DIR") (System/getProperty "user.home"))]
   [nil "--no-qr" "Suppress terminal QR code display"
    :default false]
   ["-h" "--help" "Display this usage message"]])

(defn print-banner
  "Prints a stylized console banner with local and LAN network URLs and terminal QR code."
  [{:keys [port host storage-dir dir no-qr]}]
  (let [effective-dir (or storage-dir dir (System/getProperty "user.home"))
        urls (network/format-server-urls port)
        stats (storage/get-storage-stats effective-dir)
        primary-lan-url (or (-> urls :network first :url)
                            (:local urls))
        border (apply str (repeat 58 "═"))]
    (println)
    (println border)
    (println "  🕊️  FILE PIGEON - Local Network HTTP Server")
    (println border)
    (println (format "  📁 Storage Folder: %s" (:directory stats)))
    (println (format "  💾 Usable Space:  %s" (:usable-space-formatted stats)))
    (println (format "  🌐 Bound Host:    %s:%d" host port))
    (println)
    (println "  Access URLs:")
    (println (format "    • Localhost: %s" (:local urls)))
    (doseq [{:keys [interface ip url]} (:network urls)]
      (println (format "    • Network:   %s  (%s: %s)" url interface ip)))
    (when-not no-qr
      (println)
      (println "  📱 Scan to connect instantly from mobile phone:")
      (println)
      (print (qr/to-terminal-string primary-lan-url)))
    (println border)
    (println "  Press Ctrl+C to stop the server.")
    (println border)
    (println)))

(defn- setup-shutdown-hook! [server]
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread.
    (fn []
      (println "\nReceived shutdown signal. Stopping server...")
      (server/stop-server server)
      (println "File Pigeon server stopped cleanly. Goodbye! 🕊️")))))

(defn -main
  "Main entry point."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (do
        (println "File Pigeon - Local Network HTTP File Transfer Service\n")
        (println "Usage: clojure -M:run [options]")
        (println "       lein run [options]\n")
        (println "Options:")
        (println summary)
        (System/exit 0))

      errors
      (do
        (println "Error parsing options:")
        (doseq [err errors]
          (println "  -" err))
        (System/exit 1))

      :else
      (let [server (server/start-server (assoc options :join? false))]
        (setup-shutdown-hook! server)
        (print-banner options)
        ;; Keep main thread alive while Jetty daemon runs
        (try
          (.join server)
          (catch InterruptedException _
            (server/stop-server server)))))))

;; REPL Development Convenience
(defn start!
  ([] (start! {}))
  ([opts]
   (let [default-dir (or (System/getenv "SHARED_DIR") (System/getProperty "user.home"))
         options (merge {:port 8080 :host "0.0.0.0" :storage-dir default-dir} opts)
         srv (server/start-server (assoc options :join? false))]
     (print-banner options)
     srv)))

(defn stop! []
  (server/stop-server))

(defn restart!
  ([] (restart! {}))
  ([opts]
   (stop!)
   (start! opts)))

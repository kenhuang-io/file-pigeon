(ns file-pigeon.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [file-pigeon.routes :refer [create-routes]]
            [file-pigeon.middleware :refer [wrap-app-stack]]
            [file-pigeon.storage :as storage]))

(defonce ^:private current-server (atom nil))

(defn create-app-handler
  "Builds the full Ring handler pipeline."
  [storage-dir port]
  (-> (create-routes storage-dir port)
      wrap-app-stack))

(defn start-server
  "Starts the Ring Jetty web server.
   Options:
   - :port (int, default 8080)
   - :host (string, default \"0.0.0.0\")
   - :storage-dir (string, default HOME)
   - :join? (bool, default false)"
  [{:keys [host port storage-dir dir join?]
    :or {host "0.0.0.0"
         port 8080
         join? false}}]
  (let [effective-dir (or storage-dir dir (System/getProperty "user.home"))]
    (storage/ensure-storage-dir! effective-dir)
    (let [handler (create-app-handler effective-dir port)
          server (run-jetty handler {:host host
                                     :port port
                                     :join? join?})]
      (reset! current-server server)
      server)))

(defn stop-server
  "Stops the currently running Jetty server if any."
  ([]
   (when-let [server @current-server]
     (stop-server server)
     (reset! current-server nil)))
  ([^org.eclipse.jetty.server.Server server]
   (when server
     (println "Stopping HTTP server...")
     (.stop server))))

(defn restart-server
  "Restarts the running server with new or existing options."
  ([]
   (restart-server {}))
  ([opts]
   (stop-server)
   (start-server opts)))

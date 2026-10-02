(ns file-pigeon.network
  (:import [java.net NetworkInterface Inet4Address]))

(defn get-lan-interfaces
  "Returns a seq of non-loopback, active IPv4 network interface records."
  []
  (try
    (let [interfaces (enumeration-seq (NetworkInterface/getNetworkInterfaces))]
      (doall
       (for [ni interfaces
             :when (try
                     (and (.isUp ni)
                          (not (.isLoopback ni))
                          (not (.isPointToPoint ni)))
                     (catch Exception _ false))
             addr (enumeration-seq (.getInetAddresses ni))
             :when (and (instance? Inet4Address addr)
                        (not (.isLoopbackAddress addr))
                        (not (.isLinkLocalAddress addr)))]
         {:interface (.getName ni)
          :display-name (.getDisplayName ni)
          :ip (.getHostAddress addr)})))
    (catch Exception e
      (println "Warning: Failed to enumerate network interfaces:" (.getMessage e))
      [])))

(defn get-primary-lan-ip
  "Returns the first preferred IPv4 address (e.g., Wi-Fi or Ethernet), or 127.0.0.1 if none found."
  []
  (let [interfaces (get-lan-interfaces)]
    (or (some (fn [{:keys [interface ip]}]
                ;; Prefer standard wireless (wl*, wlan*) or ethernet (eth*, en*) interfaces
                (when (re-find #"^(wl|eth|en)" interface)
                  ip))
              interfaces)
        (:ip (first interfaces))
        "127.0.0.1")))

(defn format-server-urls
  "Returns formatted URLs for local host and all discovered LAN interfaces."
  [port]
  (let [interfaces (get-lan-interfaces)]
    {:local (format "http://localhost:%d" port)
     :network (mapv (fn [{:keys [interface ip]}]
                      {:interface interface
                       :ip ip
                       :url (format "http://%s:%d" ip port)})
                    interfaces)}))

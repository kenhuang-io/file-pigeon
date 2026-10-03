(ns file-pigeon.middleware
  (:require [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn wrap-cors
  "Adds permissive CORS headers for local network accessibility."
  [handler]
  (fn [request]
    (if (= (:request-method request) :options)
      {:status 204
       :headers {"Access-Control-Allow-Origin" "*"
                 "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS, HEAD"
                 "Access-Control-Allow-Headers" "Content-Type, Authorization, Accept, X-Requested-With"
                 "Access-Control-Max-Age" "86400"}
       :body ""}
      (when-let [response (handler request)]
        (update-in response [:headers] assoc
                   "Access-Control-Allow-Origin" "*"
                   "Access-Control-Allow-Headers" "Content-Type, Authorization, Accept, X-Requested-With")))))

(defn wrap-json-body
  "Serializes Clojure maps or seqs in :body to JSON strings if body is a collection."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and (map? response)
               (coll? (:body response)))
        (-> response
            (assoc :body (json/generate-string (:body response) {:pretty true}))
            (assoc-in [:headers "Content-Type"] "application/json; charset=utf-8"))
        response))))

(defn wrap-request-logging
  "Logs incoming requests and elapsed processing time to stdout."
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          remote (or (get-in request [:headers "x-forwarded-for"])
                     (:remote-addr request)
                     "unknown")
          method (-> request :request-method name str/upper-case)
          uri (:uri request)
          response (handler request)
          elapsed (- (System/currentTimeMillis) start)
          status (:status response 200)]
      (println (format "[%s] %s %s -> %d (%dms)" remote method uri status elapsed))
      response)))

(defn wrap-error-handling
  "Catches uncaught exceptions and produces a formatted 500 error response."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (println "Unhandled exception in request:" (.getMessage t))
        (.printStackTrace t)
        (let [accept (get-in request [:headers "accept"] "")]
          (if (str/includes? accept "application/json")
            {:status 500
             :headers {"Content-Type" "application/json; charset=utf-8"}
             :body (json/generate-string {:error "Internal Server Error"
                                          :message (.getMessage t)})}
            {:status 500
             :headers {"Content-Type" "text/html; charset=utf-8"}
             :body (str "<h1>500 Internal Server Error</h1><pre>" (.getMessage t) "</pre>")})) ))))

(defn wrap-app-stack
  "Composes the full Ring middleware pipeline."
  [handler]
  (-> handler
      wrap-json-body
      wrap-cors
      wrap-keyword-params
      wrap-params
      wrap-multipart-params
      wrap-request-logging
      wrap-error-handling))

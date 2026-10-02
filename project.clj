(defproject file-pigeon "0.1.0-SNAPSHOT"
  :description "Local network HTTP server and file sharing service"
  :url "https://github.com/kenhuang-io/file-pigeon"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [ring/ring-core "1.13.0"]
                 [ring/ring-jetty-adapter "1.13.0"]
                 [ring/ring-defaults "0.5.0"]
                 [compojure "1.7.1"]
                 [cheshire "5.13.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [com.google.zxing/core "3.5.3"]
                 [com.google.zxing/javase "3.5.3"]
                 [org.slf4j/slf4j-nop "2.0.16"]]
  :main ^:skip-aot file-pigeon.core
  :target-path "target/%s"
  :profiles {:dev {:dependencies [[ring/ring-mock "0.4.0"]]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

(defproject com.clojure-goes-fast/virgil "0.2.1"
  :license {:name "MIT License"}
  :dependencies [[org.ow2.asm/asm "9.7"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.11.2"]]}
             :1.12 {:dependencies [[org.clojure/clojure "1.12.0-alpha9"]]}
             :1.11 {:dependencies [[org.clojure/clojure "1.11.2"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}})

(defproject com.clojure-goes-fast/virgil "0.2.1"
  :license {:name "MIT License"}
  :dependencies [[org.ow2.asm/asm "9.7"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.11.3"]]}
             :1.12 {:dependencies [[org.clojure/clojure "1.12.0-alpha10"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}})

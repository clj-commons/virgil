(defproject com.clojure-goes-fast/virgil "0.2.0"
  :license {:name "MIT License"}
  :dependencies [[org.ow2.asm/asm "9.5"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :1.11 {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}})

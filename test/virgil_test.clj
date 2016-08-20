(ns virgil-test
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [virgil :as v]
   [clojure.test :refer :all]))

(defn magic-number []
  (let [c (Class/forName "virgil.Test" false (clojure.lang.RT/makeClassLoader))]
    (eval `(. ~c magicNumber))))

(deftest test-watch
  (.mkdirs (io/file "/tmp/virgil/virgil"))
  (virgil/watch "/tmp/virgil")
  (Thread/sleep 5000)
  (sh/sh "cp" "test/a.java" "/tmp/virgil/virgil/Test.java")
  (Thread/sleep 2000)
  (is (= 24 (magic-number)))
  (sh/sh "cp" "test/b.java" "/tmp/virgil/virgil/Test.java")
  (Thread/sleep 2000)
  (is (= 42 (magic-number))))

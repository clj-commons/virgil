(ns virgil-test
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [virgil]
   [clojure.test :refer :all]))

(defn magic-number []
  (let [cl (clojure.lang.RT/makeClassLoader)
        c  (Class/forName "virgil.Test" false cl)]
    (eval `(. (new ~c) magicNumber))))

(defn cp [file class]
  (sh/sh "cp" (str "test/" file ".java") (str "/tmp/virgil/virgil/" class ".java")))

(defn wait []
  (Thread/sleep 1e4))

(deftest test-watch
  (sh/sh "rm" "-rf" "/tmp/virgil")
  (.mkdirs (io/file "/tmp/virgil/virgil"))
  (virgil/watch-and-recompile ["/tmp/virgil"]
                              :options ["-Xlint:all"]
                              :verbose true)
  (wait)
  (cp 'a 'ATest)
  (cp 'c 'Test)
  (wait)
  (is (= 24 (magic-number)))

  (cp 'd 'Test)
  (wait)
  (is (= 25 (magic-number)))

  (cp 'b 'ATest)
  (wait)
  (is (= 43 (magic-number)))

  (cp 'c 'Test)
  (wait)
  (is (= 42 (magic-number))))

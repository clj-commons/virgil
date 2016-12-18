(ns virgil-test
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [virgil :as v]
   [clojure.test :refer :all]))

(defn magic-number []
  (let [cl (clojure.lang.RT/makeClassLoader)
        c (Class/forName "virgil.Wrapper" false cl)]
    (eval `(. ~c magicNumber))))

(defn cp [file class]
  (sh/sh "cp" (str "test/" file ".java") (str "/tmp/virgil/virgil/" class ".java"))
  (Thread/sleep 2000))

(deftest test-watch
  (sh/sh "rm" "-rf" "/tmp/virgil")
  (.mkdirs (io/file "/tmp/virgil/virgil"))
  (virgil/watch "/tmp/virgil")

  (cp 'a 'Test)
  (cp 'c 'Wrapper)
  (is (= 24 (magic-number)))

  (cp 'd 'Wrapper)
  (is (= 25 (magic-number)))

  (cp 'b 'Test)
  (is (= 43 (magic-number)))

  (cp 'c 'Wrapper)
  (is (= 42 (magic-number))))

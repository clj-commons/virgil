(ns virgil-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            virgil)
  (:import java.nio.file.Files
           java.nio.file.attribute.FileAttribute))

;; Sanity check we run the Clojure version which we think we do.
(deftest clojure-version-sanity-check
  (let [v (System/getenv "CLOJURE_VERSION")]
    (println "Running on Clojure" (clojure-version))
    (when v (is (clojure.string/starts-with? (clojure-version) v)))))

(def ^:dynamic *dir*)

(defn mk-tmp []
  (.toFile (Files/createTempDirectory "virgil" (into-array FileAttribute []))))

(defn magic-number []
  (let [cl (clojure.lang.RT/makeClassLoader)
        c  (Class/forName "virgil.B" false cl)]
    (eval `(. (new ~c) magicNumber))))

(defn cp [file class]
  (let [dest (io/file *dir* "virgil")]
    (.mkdir dest)
    (io/copy (io/file "test" (str file ".java"))
             (io/file dest (str class ".java")))))

(defn wait []
  (Thread/sleep 500))

(defn wait-until-true [f]
  (loop [i 10] ;; Max 10 tries
    (when (pos? i)
      (wait)
      (when-not (f) (recur (dec i))))))

(defn recompile []
  (virgil/compile-java [(str *dir*)]))

(deftest manual-compile-test
  (binding [*dir* (mk-tmp)]
    (cp "A" 'A)
    (cp "B" 'B)
    (recompile)
    (is (= 24 (magic-number)))

    (cp "Balt" 'B)
    (recompile)
    (is (= 25 (magic-number)))

    (cp "Aalt" 'A)
    (recompile)
    (is (= 43 (magic-number)))

    (cp "B" 'B)
    (recompile)
    (is (= 42 (magic-number)))))

;; This test is commented out because file watcher service is not realiable.
;; TODO: investigate if stability can be improved.
#_
(deftest watch-and-recompile-test
  (binding [*dir* (mk-tmp)]
    (virgil/watch-and-recompile [(str *dir*)])
    (cp "A" 'A)
    (cp "B" 'B)
    (wait-until-true #(= 24 (magic-number)))
    (is (= 24 (magic-number)))

    (cp "Balt" 'B)
    (wait-until-true #(= 25 (magic-number)))
    (is (= 25 (magic-number)))

    (cp "Aalt" 'A)
    (wait-until-true #(= 43 (magic-number)))
    (is (= 43 (magic-number)))

    (cp "B" 'B)
    (wait-until-true #(= 42 (magic-number)))
    (is (= 42 (magic-number)))))

(deftest warnings-shouldnt-throw-test
  (binding [*dir* (mk-tmp)]
    (cp "ClassWithWarning" 'ClassWithWarning)
    (is (nil? (recompile))))

  (binding [*dir* (mk-tmp)]
    (cp "ClassWithError" 'ClassWithError)
    (is (thrown? clojure.lang.ExceptionInfo (recompile)))))

(deftest errors-shouldnt-break-watch-and-recompile-test
  (binding [*dir* (mk-tmp)]
    (cp "ClassWithError" 'ClassWithError)
    (is (nil? (virgil/watch-and-recompile [(str *dir*)])))))

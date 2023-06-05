(ns example
  (:require virgil))

(defn -main [& args]
  (virgil/watch-and-recompile ["src"] :verbose true)
  (assert (= 42 (eval '(.magicNumber (example.Test.))))))

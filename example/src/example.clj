(ns example
  (:require virgil))

(defn -main [& args]
  (virgil/watch-and-recompile ["src"] :verbose true)
  (assert (= 84 (eval '(.magicNumber (example.ExampleChild.))))))

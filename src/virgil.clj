(ns virgil
  (:require
   [clojure.java.io :as io]
   [clojure.tools.namespace.repl :refer (refresh-all)]
   [virgil.watch :refer (watch-directory)]
   [virgil.compile :refer (compile-all-java)]))

(def watches (atom #{}))

(defn watch [& directories]
  (doseq [d directories]
    (let [prefix (.getCanonicalPath (io/file d))]
      (when-not (contains? @watches prefix)
        (let [recompile (fn []
                          (println (str "\nrecompiling all files in " d))
                          (compile-all-java d)
                          ;; We need to create a thread binding for *ns* so that
                          ;; refresh-all can use in-ns.
                          (binding [*ns* *ns*]
                            (refresh-all)))]
          (swap! watches conj prefix)
          (watch-directory (io/file d)
            (fn [f]
              (when (and (.endsWith (str f) ".java")
                      (not (.contains (str f) ".#")))
                (recompile))))
          (recompile))))))

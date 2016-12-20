(ns virgil
  (:require
   [clojure.java.io :as io]
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
                          (doseq [ns (all-ns)]
                            (try
                              (require (symbol (str ns)) :reload)
                              (catch Throwable e
                                ))))]
          (swap! watches conj prefix)
          (watch-directory (io/file d)
            (fn [f]
              (when (.endsWith (str f) ".java")
                (recompile))))
          (recompile))))))

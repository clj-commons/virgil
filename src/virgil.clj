(ns virgil
  (:require
   [clojure.java.io :as io]
   [virgil.watch :refer (watch-directory make-idle-callback)]
   [virgil.compile :refer (compile-all-java java-file?)]
   virgil.util))

(def watches (atom #{}))

(defn compile-java [directories & {:keys [options verbose]}]
  (let [diags (compile-all-java directories options verbose)]
    (when (virgil.util/compilation-errored? diags)
      (throw (ex-info (format "Compilation failed: %d error(s)." (count diags))
                      {:diagnostics diags})))))

(defn watch-and-recompile [directories & {:keys [options verbose post-hook]}]
  (let [recompile (fn []
                    (compile-all-java directories options verbose)
                    (when post-hook
                      (post-hook)))
        schedule-recompile (make-idle-callback recompile 100)]
    (recompile)
    (doseq [d directories]
      (let [prefix (.getCanonicalPath (io/file d))]
        (when-not (contains? @watches prefix)
          (swap! watches conj prefix)
          (watch-directory (io/file d)
                           (fn [f]
                             (when (java-file? (str f))
                               (schedule-recompile)))))))))

(defn ^:deprecated watch [& directories]
  (watch-and-recompile directories))

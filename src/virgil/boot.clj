(ns virgil.boot
  "This namespace should only be loaded by Boot."
  {:boot/export-tasks true}
  (:require [boot.core :as core]
            [boot.util :as util]
            [virgil.compile :as compile]
            [virgil.util :refer [print-diagnostics]])
  (:import javax.tools.DiagnosticCollector
           java.util.Arrays))

(core/deftask javac*
  "Dynamically compile Java sources."
  [o options OPTIONS [str] "List of options passed to the java compiler."
   v verbose         bool  "Print each compiled classname"]
  (let [tgt (core/tmp-dir!)]
    (core/with-pre-wrap [fs]
      (let [diag-coll (DiagnosticCollector.)
            opts      (->> ["-d"  (.getPath tgt)
                            "-cp" (System/getProperty "boot.class.path")]
                           (concat options)
                           (into-array String) Arrays/asList)
            tmp-srcs  (some->> (core/input-files fs)
                               (core/by-ext [".java"]))]
        (when (seq tmp-srcs)
          (util/info "Compiling %d Java source files...\n" (count tmp-srcs))
          (binding [compile/*print-compiled-classes* verbose]
            (compile/compile-java
             opts diag-coll
             (into {}
                   (for [tmp-src tmp-srcs
                         :let [prefix (str (core/tmp-dir tmp-src))
                               file (core/tmp-file tmp-src)
                               class-name (compile/file->class
                                           prefix file)]]
                     [class-name (slurp file)]))))

          (print-diagnostics diag-coll)))
      (-> fs (core/add-resource tgt) core/commit!))))

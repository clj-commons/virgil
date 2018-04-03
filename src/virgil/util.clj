(ns virgil.util
  "Utilities for cross-tooling."
  (:import (javax.tools DiagnosticCollector Diagnostic$Kind)))

(defn- println-err [& args]
  (binding [*err* *out*]
    (apply println args)))

(defn- infer-print-function
  "Infer the function to print the compilation event with depending on the
  environment (Leiningen, Boot)."
  [^Diagnostic$Kind diagnostic-kind]
  (let [tool (or (try (require 'leiningen.core.main) :lein
                      (catch Exception _))
                 (try (require 'boot.util) :boot
                      (catch Exception _)))
        err-fn (case tool
                 :lein (resolve 'leiningen.core.main/warn)
                 :boot (resolve 'boot.util/fail)
                 println-err)
        warn-fn (case tool
                  :lein (resolve 'leiningen.core.main/warn)
                  :boot (resolve 'boot.util/warn)
                  println-err)
        info-fn (case tool
                  :lein (resolve 'leiningen.core.main/info)
                  :boot (resolve 'boot.util/info)
                  println-err)]
    (condp = diagnostic-kind
      Diagnostic$Kind/ERROR err-fn
      Diagnostic$Kind/WARNING warn-fn
      Diagnostic$Kind/MANDATORY_WARNING warn-fn
      info-fn)))

(defn print-diagnostics [^DiagnosticCollector diag-coll]
  (doseq [d (.getDiagnostics diag-coll)]
    (let [k (.getKind d)
          log (infer-print-function k)]
      (if (nil? (.getSource d))
        (log (format "%s: %s\n"
                     (.toString k)
                     (.getMessage d nil)))
        (log (format "%s: %s, line %d: %s\n"
                     (.toString k)
                     (.. d getSource getName)
                     (.getLineNumber d)
                     (.getMessage d nil)))))))

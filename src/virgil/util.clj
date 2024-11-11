(ns virgil.util
  "Utilities for cross-tooling."
  (:import (javax.tools DiagnosticCollector Diagnostic$Kind)))

(defn println-err [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn- infer-print-function
  "Infer the function to print the compilation event with."
  [^Diagnostic$Kind diagnostic-kind]
  (condp = diagnostic-kind
    Diagnostic$Kind/ERROR println-err
    Diagnostic$Kind/WARNING println-err
    Diagnostic$Kind/MANDATORY_WARNING println-err
    println))

(defn print-diagnostics [^DiagnosticCollector diag-coll]
  (doseq [d (.getDiagnostics diag-coll)]
    (let [k (.getKind d)
          log (infer-print-function k)]
      (if (nil? (.getSource d))
        (println-err (format "%s: %s\n"
                             (.toString k)
                             (.getMessage d nil)))
        (println-err (format "%s: %s, line %d: %s\n"
                     (.toString k)
                     (.. d getSource getName)
                     (.getLineNumber d)
                     (.getMessage d nil)))))))

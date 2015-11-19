(ns virgil
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [filevents.core :as fe])
  (:import
    [javax.tools
     DiagnosticCollector
     ForwardingJavaFileManager
     JavaCompiler
     JavaFileObject$Kind
     SimpleJavaFileObject
     StandardJavaFileManager
     ToolProvider]
    [clojure.lang
     DynamicClassLoader]
    [java.io
     ByteArrayOutputStream]))

;; a shout-out to https://github.com/tailrecursion/javastar, which
;; provided a map for this territory

(defn source-object
  [class-name source]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name \. \/)
                                 (. JavaFileObject$Kind/SOURCE extension)))
       JavaFileObject$Kind/SOURCE]
      (getCharContent [_] source)))

(defn class-object
  "Returns a JavaFileObject to store a class file's bytecode."
  [class-name baos]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name \. \/)
                                 (. JavaFileObject$Kind/CLASS extension)))
       JavaFileObject$Kind/CLASS]
    (openOutputStream [] baos)))

(defn class-manager
  [cl manager cache]
  (proxy [ForwardingJavaFileManager] [manager]
    (getClassLoader [location]
      cl)
    (getJavaFileForOutput [location class-name kind sibling]
      (class-object class-name
        (-> cache
          (swap! assoc class-name (ByteArrayOutputStream.))
          (get class-name))))))

(defn compile-java
  [class-name source]
  (let [compiler (ToolProvider/getSystemJavaCompiler)
        diag     (DiagnosticCollector.)
        cache    (atom {})
        cl       (clojure.lang.RT/makeClassLoader)
        mgr      (class-manager cl (.getStandardFileManager compiler nil nil nil) cache)
        task     (.getTask compiler nil mgr diag nil nil [(source-object class-name source)])]
    (if (.call task)
      (doseq [[k ^ByteArrayOutputStream v] @cache]
        (.defineClass ^DynamicClassLoader cl k (.toByteArray v) nil))
      (throw
        (RuntimeException.
          (apply str
            (interleave (.getDiagnostics diag) (repeat "\n\n"))))))))

(defonce watches (atom #{}))

(defn watch [& directories]
  (doseq [d directories]
    (let [prefix (.getCanonicalPath (io/file d))]
      (when-not (contains? @watches prefix)
        (swap! watches conj prefix)
        (fe/watch
          (fn [action ^java.io.File file]
            (let [path (.getCanonicalPath file)]
              (when (.endsWith path ".java")
                (let [path' (.substring path (count prefix) (- (count path) 5))
                      classname (->> (str/split path' #"/")
                                  (remove empty?)
                                  (interpose ".")
                                  (apply str))]
                  (try

                    (println "\ncompiling" classname)
                    (compile-java classname (slurp file))
                    (doseq [ns (all-ns)]
                      (when (->> (ns-imports ns)
                              vals
                              (some #(= classname (.getCanonicalName %))))
                        (require (symbol (str ns)) :reload)))

                    (catch Throwable e
                      #_(.printStackTrace e)
                      (println (.getMessage e))))))))
          d)))))

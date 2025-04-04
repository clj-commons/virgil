(ns virgil.compile
  (:require
   [clojure.java.io :as io]
   [virgil.watch :as watch]
   [virgil.decompile :as decompile]
   [virgil.util :refer [print-diagnostics]]
   [clojure.string :as str])
  (:import
   [clojure.lang
    DynamicClassLoader]
   [java.io
    File
    ByteArrayOutputStream]
   [java.net
    URL
    URLClassLoader]
   java.util.ArrayList
   [java.util.concurrent
    ConcurrentHashMap]
   [javax.tools
    DiagnosticCollector
    ForwardingJavaFileManager
    JavaFileObject$Kind
    SimpleJavaFileObject
    ToolProvider]))

;; a shout-out to https://github.com/tailrecursion/javastar, which
;; provided a map for this territory

(def ^ConcurrentHashMap class-cache
  (-> (.getDeclaredField clojure.lang.DynamicClassLoader "classCache")
    (doto (.setAccessible true))
    (.get nil)))

(defn source-object
  [class-name source]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name "." "/")
                                 (. JavaFileObject$Kind/SOURCE extension)))
       JavaFileObject$Kind/SOURCE]
      (getCharContent [_] source)))

(defn class-object
  "Returns a JavaFileObject to store a class file's bytecode."
  [class-name baos]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name "." "/")
                                 (. JavaFileObject$Kind/CLASS extension)))
       JavaFileObject$Kind/CLASS]
    (openOutputStream [] baos)))

(defn source-output-object
  "Returns a JavaFileObject to store source code generated by annotation processors.

  this is needed when using annotation processors. Annotation processors will
  generate temporary java source code."
  [class-name]
  (let [baos (ByteArrayOutputStream.)]
    (proxy [SimpleJavaFileObject]
        [(java.net.URI/create (str "string:///"
                                   (.replace ^String class-name "." "/")
                                   (. JavaFileObject$Kind/SOURCE extension)))
         JavaFileObject$Kind/SOURCE]
      (getCharContent [_]
        (String. (.toByteArray baos)))
      (openOutputStream []
        baos))))

(defn class-manager
  [cl manager cache]
  (proxy [ForwardingJavaFileManager] [manager]
    (getClassLoader [location]
      cl)
    (getJavaFileForOutput [location class-name kind sibling]
      (if (= kind JavaFileObject$Kind/SOURCE)
        (source-output-object class-name)
        (do
          (.remove class-cache class-name)
          (class-object class-name
                        (-> cache
                            (swap! assoc class-name (ByteArrayOutputStream.))
                            (get class-name))))))))

(defn get-java-compiler
  "Return an instance of Java compiler."
  []
  (ToolProvider/getSystemJavaCompiler))

(defn source->bytecode [opts diag name->source]
  (let [compiler (or (get-java-compiler)
                     (throw (Exception. "Can't create the Java compiler (are you on JRE?)")))
        cache    (atom {})
        mgr      (class-manager nil (.getStandardFileManager compiler nil nil nil) cache)
        task     (.getTask compiler nil mgr diag opts nil
                           (mapv (fn [[k v]] (source-object k v)) name->source))]
    (when (.call task)
      (zipmap
       (keys @cache)
       (->> @cache
            vals
            (map #(.toByteArray ^ByteArrayOutputStream %)))))))

(def ^:dynamic *print-compiled-classes* false)

(defn compile-java
  [opts diag name->source]
  (when-not (empty? name->source)
    (let [cl              (clojure.lang.RT/makeClassLoader)
          class->bytecode (source->bytecode opts diag name->source)
          rank-order      (decompile/rank-order class->bytecode)]

      (doseq [[class bytecode] (sort-by #(-> % key rank-order) class->bytecode)]
        (when *print-compiled-classes*
          (println (str "  " class)))
        (.defineClass ^DynamicClassLoader cl class bytecode nil))

      class->bytecode)))

(defn java-file?
  [path]
  (let [base-name (.getName (io/file path))]
    (and
      (.endsWith base-name ".java")
      (not (.startsWith base-name ".#")))))

(defn file->class [^String prefix ^File f]
  (let [path (str f)]
    (when (java-file? path)
      (let [path' (.substring path (count prefix) (- (count path) 5))]
        (->> (str/split path' #"/|\\")
          (remove empty?)
          (interpose ".")
          (apply str))))))

(defn generate-classname->source
  "Given the list of directories, return a map of all Java classes within those
  directories to their source files."
  [directories]
  (into {}
        (for [dir directories
              file (watch/all-files (io/file dir))
              :let [class (file->class dir file)]
              :when class]
          [class (slurp file)])))

(defn compile-all-java
  ([directories] (compile-all-java directories nil false))
  ([directories options verbose?]
   (let [collector (DiagnosticCollector.)
         options (ArrayList. (vec options))
         name->source (generate-classname->source directories)]
     (println "\nCompiling" (count name->source)"Java source files in" directories "...")
     (binding [*print-compiled-classes* verbose?]
       (compile-java options collector name->source))
     (when-let [diags (seq (.getDiagnostics collector))]
       (print-diagnostics diags)
       diags))))

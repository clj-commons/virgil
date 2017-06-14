(ns virgil.compile
  (:require
   [clojure.java.io :as io]
   [virgil.watch :as watch]
   [virgil.decompile :as decompile]
   [clojure.string :as str])
  (:import
   [clojure.lang
    DynamicClassLoader]
   [java.io
    File
    ByteArrayOutputStream]
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
                                 (.replace ^String class-name "." File/separator)
                                 (. JavaFileObject$Kind/SOURCE extension)))
       JavaFileObject$Kind/SOURCE]
      (getCharContent [_] source)))

(defn class-object
  "Returns a JavaFileObject to store a class file's bytecode."
  [class-name baos]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name "." File/separator)
                                 (. JavaFileObject$Kind/CLASS extension)))
       JavaFileObject$Kind/CLASS]
    (openOutputStream [] baos)))

(defn class-manager
  [cl manager cache]
  (proxy [ForwardingJavaFileManager] [manager]
    (getClassLoader [location]
      cl)
    (getJavaFileForOutput [location class-name kind sibling]
      (.remove class-cache class-name)
      (class-object class-name
        (-> cache
          (swap! assoc class-name (ByteArrayOutputStream.))
          (get class-name))))))

(defn source->bytecode [name->source]
  (let [compiler (ToolProvider/getSystemJavaCompiler)
        diag     (DiagnosticCollector.)
        cache    (atom {})
        mgr      (class-manager nil (.getStandardFileManager compiler nil nil nil) cache)
        task     (.getTask compiler nil mgr diag nil nil
                   (->> name->source
                     (map #(source-object (key %) (val %)))
                     vec))]
    (if (.call task)
      (zipmap
        (keys @cache)
        (->> @cache
          vals
          (map #(.toByteArray ^ByteArrayOutputStream %))))
      (throw
        (RuntimeException.
          (apply str
            (interleave (.getDiagnostics diag) (repeat "\n\n"))))))))

(defn compile-java
  [name->source]
  (when-not (empty? name->source)
    (let [cl (clojure.lang.RT/makeClassLoader)
          class->bytecode (source->bytecode name->source)
          rank-order (decompile/rank-order class->bytecode)]

      (doseq [[class bytecode] (sort-by #(-> % key rank-order) class->bytecode)]
        (.defineClass ^DynamicClassLoader cl class bytecode nil))

      class->bytecode)))

(defn file->class [^String prefix ^File f]
  (let [path (str f)]
    (when (.endsWith path ".java")
      (let [path' (.substring path (count prefix) (- (count path) 5))]
        (->> (str/split path' #"/")
          (remove empty?)
          (interpose ".")
          (apply str))))))

(defn compile-all-java [directory]
  (->> (watch/all-files (io/file directory))
    (map (fn [f] [(file->class directory f) f]))
    (remove #(-> % first nil?))
    (map (fn [[c f]] [c (slurp f)]))
    (into {})
    compile-java))

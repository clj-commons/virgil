(ns virgil
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.set :as set])
  (:import
    [java.util.concurrent
     ConcurrentHashMap]
    [javax.tools
     DiagnosticCollector
     ForwardingJavaFileManager
     JavaCompiler
     JavaFileObject$Kind
     SimpleJavaFileObject
     StandardJavaFileManager
     ToolProvider]
    [java.nio.file
     FileSystems
     Path
     Paths
     WatchEvent$Kind
     WatchEvent$Modifier
     StandardWatchEventKinds]
    [com.sun.nio.file
     SensitivityWatchEventModifier]
    [clojure.lang
     DynamicClassLoader]
    [java.io
     ByteArrayOutputStream]))

(defn watch-service []
  (-> (FileSystems/getDefault) .newWatchService))

(defn path [path]
  (Paths/get path (make-array String 0)))

(defn register-watch [watch-service directory]
  (.register (path directory) watch-service
    (into-array WatchEvent$Kind
      [StandardWatchEventKinds/ENTRY_CREATE
       StandardWatchEventKinds/ENTRY_MODIFY])
    (into-array WatchEvent$Modifier
      [SensitivityWatchEventModifier/HIGH])))

(let [cnt (atom 0)]
  (defn watch-directory [directory f]
    (doto
      (Thread.
        (fn []
          (let [w (doto (watch-service)
                    (register-watch directory))]
            (loop []
              (let [k (.take w)]
                (doseq [e (.pollEvents k)]
                  (when-let [^Path p (.context e)]
                    (f (.toFile p))))
                (.reset k)
                (recur))))))
      (.setDaemon true)
      (.setName (str "virgil-watcher-" (swap! cnt inc)))
      .start)))

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
      (.remove class-cache class-name)
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
      (do
        (doseq [[k ^ByteArrayOutputStream v] @cache]
          (.defineClass ^DynamicClassLoader cl k (.toByteArray v) nil))
        (keys @cache))
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
        (watch-directory
          (fn [^java.io.File file]
            (let [path (.getCanonicalPath file)]
              (when (.endsWith path ".java")
                (let [path' (.substring path (count prefix) (- (count path) 5))
                      classname (->> (str/split path' #"/")
                                  (remove empty?)
                                  (interpose ".")
                                  (apply str))]
                  (try

                    (println "\ncompiling" classname)
                    (let [class-names (set (compile-java classname (slurp file)))]
                      (println "compiled" (pr-str (vec class-names)))
                      (doseq [ns (all-ns)]
                        (when (->> (ns-imports ns)
                                vals
                                (map #(.getCanonicalName ^Class %))
                                set
                                (set/intersection class-names)
                                not-empty)
                          (println "reloading" (str ns))
                          (require (symbol (str ns)) :reload))))

                    (catch Throwable e
                      #_(.printStackTrace e)
                      (println (.getMessage e))))))))
          d)))))

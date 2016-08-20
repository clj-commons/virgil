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
     WatchEvent
     WatchService
     WatchEvent$Kind
     WatchEvent$Modifier
     StandardWatchEventKinds]
    [com.sun.nio.file
     SensitivityWatchEventModifier]
    [clojure.lang
     DynamicClassLoader]
    [java.io
     File
     ByteArrayOutputStream]))

(defn ^WatchService watch-service []
  (-> (FileSystems/getDefault) .newWatchService))

(defn all-files [^File dir]
  (concat
    (->> dir
      .listFiles
      (filter #(.isDirectory ^File %))
      (mapcat all-files))
    (->> dir
      .listFiles
      (remove #(.isDirectory ^File %)))))

(defn all-directories [^File dir]
  (conj
    (->> dir
      .listFiles
      (filter #(.isDirectory ^File %))
      (mapcat all-directories))
    dir))

(defn register-watch
  "Takes a mapping of keys to directories, and registers a watch on the new"
  [key->dir ^WatchService watch-service ^File directory]
  (->> directory
    all-directories
    (reduce
      (fn [key-dir ^File dir]
        (assoc key->dir
          (.register (.toPath dir) watch-service
            (into-array WatchEvent$Kind
              [StandardWatchEventKinds/ENTRY_CREATE
               StandardWatchEventKinds/ENTRY_MODIFY])
            (into-array WatchEvent$Modifier
              [SensitivityWatchEventModifier/HIGH]))
          dir))
      key->dir)))

(let [cnt (atom 0)]
  (defn watch-directory [directory f]
    (doto
      (Thread.
        (fn []
          (let [w (watch-service)
                key->dir (atom (register-watch {} w directory))]
            (loop []
              (let [k (.take w)
                    prefix (.toPath (@key->dir k ))]
                (doseq [^WatchEvent e (.pollEvents k)]
                  (when-let [^File file (-> e .context .toFile)]

                    ;; notify about new file
                    (f (.toFile (.resolve prefix (str file))))

                    ;; update watch, if it's a new directory
                    (when (and
                            (= StandardWatchEventKinds/ENTRY_CREATE (.kind e))
                            (.isDirectory file))
                      (swap! key->dir register-watch w file))))
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

(defn source->bytecode [class-name source]
  (let [compiler (ToolProvider/getSystemJavaCompiler)
        diag     (DiagnosticCollector.)
        cache    (atom {})
        mgr      (class-manager nil (.getStandardFileManager compiler nil nil nil) cache)
        task     (.getTask compiler nil mgr diag nil nil [(source-object class-name source)])]
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
  [class-name source]
  (let [cl              (clojure.lang.RT/makeClassLoader)
        class->bytecode (source->bytecode class-name source)]
    (doseq [[class-name bytecode] class->bytecode]
      (.defineClass ^DynamicClassLoader cl class-name bytecode nil))
    (keys class->bytecode)))

;;;

(def watches (atom #{}))

(defn watch [& directories]
  (doseq [d directories]
    (let [prefix (.getCanonicalPath (io/file d))]
      (when-not (contains? @watches prefix)
        (swap! watches conj prefix)
        (watch-directory (io/file d)
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
                      (println (.getMessage e)))))))))))))

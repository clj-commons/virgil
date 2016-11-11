(ns virgil.compile
  (:import
   (clojure.lang DynamicClassLoader)
   (java.io ByteArrayOutputStream)
   (java.util.concurrent ConcurrentHashMap)
   (javax.tools DiagnosticCollector
                ForwardingJavaFileManager
                JavaFileObject$Kind
                SimpleJavaFileObject
                ToolProvider)))

;; a shout-out to https://github.com/tailrecursion/javastar, which
;; provided a map for this territory

(def ^ConcurrentHashMap class-cache
  (-> (.getDeclaredField clojure.lang.DynamicClassLoader "classCache")
      (doto (.setAccessible true))
      (.get nil)))

(defn- source-object [class-name source]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name \. \/)
                                 (. JavaFileObject$Kind/SOURCE extension)))
       JavaFileObject$Kind/SOURCE]
    (getCharContent [_] source)))

(defn- class-object
  "Returns a JavaFileObject to store a class file's bytecode."
  [class-name baos]
  (proxy [SimpleJavaFileObject]
      [(java.net.URI/create (str "string:///"
                                 (.replace ^String class-name \. \/)
                                 (. JavaFileObject$Kind/CLASS extension)))
       JavaFileObject$Kind/CLASS]
    (openOutputStream [] baos)))

(defn- class-manager [cl manager cache]
  (proxy [ForwardingJavaFileManager] [manager]
    (getClassLoader [location]
      cl)
    (getJavaFileForOutput [location class-name kind sibling]
      (.remove class-cache class-name)
      (class-object class-name
                    (-> cache
                        (swap! assoc class-name (ByteArrayOutputStream.))
                        (get class-name))))))

(defn- source->bytecode [class-name source & [diagnostic-collector opts]]
  (let [compiler   (or (ToolProvider/getSystemJavaCompiler)
                       (throw (Exception. "The java compiler is not working. Please make sure you use a JDK!")))
        diagnostic-collector (or diagnostic-collector
                                 (DiagnosticCollector.))
        cache    (atom {})
        mgr      (class-manager nil (.getStandardFileManager compiler nil nil nil) cache)
        task     (.getTask compiler nil mgr diagnostic-collector opts nil [(source-object class-name source)])]
    (when (.call task)
      (zipmap
       (keys @cache)
       (->> @cache
            vals
            (map #(.toByteArray ^ByteArrayOutputStream %)))))))

(defn compile-java [class-name source & [diagnostic-collector opts]]
  (let [cl              (clojure.lang.RT/makeClassLoader)
        class->bytecode (source->bytecode class-name source diagnostic-collector opts)]
    (doseq [[class-name bytecode] class->bytecode]
      (.defineClass ^DynamicClassLoader cl class-name bytecode nil))
    (keys class->bytecode)))

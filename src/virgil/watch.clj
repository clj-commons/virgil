(ns virgil.watch
  (:import
   com.sun.nio.file.SensitivityWatchEventModifier
   java.io.File
   [java.nio.file Files FileSystems FileVisitOption
    StandardWatchEventKinds Path WatchEvent WatchEvent$Kind
    WatchEvent$Modifier WatchService]
   [java.util.concurrent LinkedBlockingQueue TimeUnit]
   [java.util.function Function Predicate]
   java.util.stream.Collectors))

(defn ^WatchService watch-service []
  (-> (FileSystems/getDefault) .newWatchService))

(defn all-files [^File dir]
  (-> (Files/walk (.toPath dir) (into-array FileVisitOption []))
      (.map (reify java.util.function.Function
              (apply [_ path]
                (.toFile ^Path path))))
      (.filter (reify java.util.function.Predicate
                 (test [_ f]
                   (.isFile f))))
      (.collect (Collectors/toList))))

(defn all-directories [^File dir]
  (-> (Files/walk (.toPath dir) (into-array FileVisitOption []))
      (.map (reify java.util.function.Function
              (apply [_ path]
                (.toFile ^Path path))))
      (.filter (reify java.util.function.Predicate
                 (test [_ f]
                   (.isDirectory f))))
      (.collect (Collectors/toList))))

(defn register-watch
  "Takes a mapping of keys to directories, and registers a watch on the new"
  [key->dir ^WatchService watch-service ^File directory]
  (->> directory
    all-directories
    (reduce
      (fn [key->dir ^File dir]
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
                    prefix (.toPath (@key->dir k))]
                (doseq [^WatchEvent e (.pollEvents k)]
                  (when-let [^File file (-> e .context .toFile)]

                    ;; notify about new file
                    (try
                      (f (.toFile (.resolve prefix (str file))))
                      (catch Throwable e
                        (println (.getMessage e))))

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

;; Debouncing logic with idle

(defn- consume-queue-and-callback-when-idle
  [queue idle-time-in-ms f]
  (loop [callback-pending false]
    (let [el (if callback-pending
               (.poll queue idle-time-in-ms TimeUnit/MILLISECONDS)
               (.take queue))]
      (if (nil? el)
        (do
          (try
            (f)
            (catch RuntimeException e
              (println (.getMessage e)))
            (catch Throwable e
              (.printStackTrace e)))
          (recur false))
        (recur true)))))

(defn make-idle-callback
  [f idle-time-in-ms]
  (let [queue (LinkedBlockingQueue.)
        start-thread (delay
                       (doto
                           (Thread. (fn []
                                      (consume-queue-and-callback-when-idle queue idle-time-in-ms f)))
                         (.setDaemon true)
                         (.setName "virgil-idle-callback")
                         .start))]
    (fn []
      @start-thread
      (.put queue :go))))

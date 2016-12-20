(ns virgil.watch
  (:import
   [com.sun.nio.file
    SensitivityWatchEventModifier]
   [java.io
    File]
   [java.nio.file
    FileSystems
    StandardWatchEventKinds
    WatchEvent
    WatchEvent$Kind
    WatchEvent$Modifier
    WatchService]))

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

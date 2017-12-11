(ns virgil
  (:require
   [clojure.java.io :as io]
   [clojure.tools.namespace.repl :refer (refresh-all)]
   [virgil.watch :refer (watch-directory)]
   [virgil.compile :refer (compile-all-java java-file?)]))

(def watches (atom #{}))

(defn- consume-queue-and-callback-when-idle
  [queue idle-time-in-ms f]
  (loop [callback-pending false]
    (let [el (if callback-pending
               (.poll queue idle-time-in-ms java.util.concurrent.TimeUnit/MILLISECONDS)
               (.take queue))]
      (if (nil? el)
        (do
          (try
            (f)
            (catch Throwable e
              (.printStackTrace e)))
          (recur false))
        (recur true)))))

(defn make-idle-callback
  [f idle-time-in-ms]
  (let [queue (java.util.concurrent.LinkedBlockingQueue.)
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

(defn watch [& directories]
  (let [recompile (fn []
                    (println (str "\nrecompiling all files in " (vec directories)))
                    (compile-all-java directories)
                    ;; We need to create a thread binding for *ns* so that
                    ;; refresh-all can use in-ns.
                    (binding [*ns* *ns*]
                      (refresh-all)))
        recompile (make-idle-callback recompile 100)]

    (doseq [d directories]
      (let [prefix (.getCanonicalPath (io/file d))]
        (recompile)
        (when-not (contains? @watches prefix)
          (swap! watches conj prefix)
          (watch-directory (io/file d)
            (fn [f]
              (when (java-file? (str f))
                (recompile)))))))

    (recompile)))

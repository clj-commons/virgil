(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [clojure.tools.build.tasks.write-pom]
            [deps-deploy.deps-deploy :as dd]))

(defn default-opts [version]
  (let [url "https://github.com/clj-commons/virgil"]
    {;; Pom section
     :lib 'virgil/virgil
     :version version
     :scm {:url url, :tag version}
     :pom-data [[:description "Recompile Java code without restarting the REPL"]
                [:url url]
                [:licenses
                 [:license
                  [:name "MIT License"]
                  [:url "https://opensource.org/license/MIT"]]]]

     ;; Build section
     :basis (b/create-basis {})
     :target "target"
     :class-dir "target/classes"}))

(defmacro opts+ [& body]
  `(let [~'opts (merge (default-opts (:version ~'opts)) ~'opts)]
     ~@body
     ~'opts))

(defn log [fmt & args] (println (apply format fmt args)))

(defn- jar-file [{:keys [target lib version]}]
  (format "%s/%s-%s.jar" target (name lib) version))

(defn clean [opts] (b/delete {:path (:target (opts+))}))

;; Hack to propagate scope into pom.
(alter-var-root
 #'clojure.tools.build.tasks.write-pom/to-dep
 (fn [f]
   (fn [[_ {:keys [mvn/scope]} :as arg]]
     (let [res (f arg)
           alias (some-> res first namespace)]
       (cond-> res
         (and alias scope) (conj [(keyword alias "scope") scope]))))))

(defn jar
  "Compile and package the JAR."
  [opts]
  (opts+
    (doto opts clean b/write-pom)
    (let [{:keys [class-dir basis]} opts
          jar (jar-file opts)]
      (println (format "Building %s..." jar))
      (b/copy-dir {:src-dirs   (:paths basis)
                   :target-dir class-dir
                   :include    "**"
                   :ignores    [#".+\.java"]})
      (b/jar (assoc opts :jar-file jar)))))

(defn deploy "Deploy the JAR to Clojars." [{:keys [version] :as opts}]
  (assert (and version (re-matches #"\d+\.\d+\.\d+.*" version)))
  (opts+
   (jar opts)
   (log "Deploying %s to Clojars..." version)
   (dd/deploy {:installer :remote
               :artifact (b/resolve-path (jar-file opts))
               :pom-file (b/pom-path opts)})))

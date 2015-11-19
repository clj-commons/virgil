(ns lein-virgil.plugin
  (:require
    [leiningen.core.eval :as eval]))

(defn middleware [project]
  (if (contains? project :java-source-paths)
    (let [project' (-> project
             (update-in [:dependencies] conj '[virgil "0.1.0"])
             (update-in [:injections] concat `((require 'virgil) (virgil/watch ~@(:java-source-paths project)))))]
      project')
    project))

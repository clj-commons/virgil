(ns virgil.decompile
  (:require
   [clojure.string :as str])
  (:import
   [org.objectweb.asm
    ClassReader])
  (:refer-clojure :exclude [parents]))

(defn normalize-class-name [^String s]
  (str/replace s #"/" "."))

(defn parents [^bytes bytecode]
  (let [r (ClassReader. bytecode)]
    (list*
      (normalize-class-name (.getSuperName r))
      (map normalize-class-name (.getInterfaces r)))))

(defn rank-order
  [class->bytecode]
  (let [parents        (fn [class]
                         (->> class
                           class->bytecode
                           parents
                           (filter class->bytecode)))
        class->parents (zipmap
                         (keys class->bytecode)
                         (->> class->bytecode
                           keys
                           (map parents)))
        ranked         (zipmap
                         (->> class->parents
                           (filter #(-> % val empty?))
                           (map key))
                         (repeat 0))
        unranked       (apply dissoc class->parents (keys ranked))]

    (loop [ranked ranked, unranked unranked, rank 1]
      (if (empty? unranked)
        ranked
        (let [children (->> unranked
                         (filter #(->> % val (every? ranked)))
                         (map key))]
          (recur
            (merge ranked (zipmap children (repeat rank)))
            (apply dissoc unranked children)
            (inc rank)))))))

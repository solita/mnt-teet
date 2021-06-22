(ns generate-schema
  (:require [datomic.client.api :as d]
            [clojure.data.csv :as csv]
            [clojure.string :refer [join split]]
            [clojure.set :refer [difference]]))

;; we use this to keep traversal acyclic
(def descend-blacklist #{:permission/projects})

(def datomic-client-config (read-string (slurp "config.edn")))

(when-let [p (get datomic-client-config :aws-profile)]
  (System/setProperty "aws.profile" p))

(def main-conn (d/connect (d/client datomic-client-config)
                          {:db-name (get datomic-client-config :main-db-name)}))

(def asset-conn (d/connect (d/client datomic-client-config)
                           {:db-name (get datomic-client-config :asset-db-name)}))


(defn field-names [e]
  (let [kv-pairs-with-db-id (filter (comp :db/id second) e)
        ref-key-set (into #{}
                          (map first kv-pairs-with-db-id))
        all-keys-set (set (keys e))]
    (into [] (concat (map #(str  %1 " (entity ref)")
                          ref-key-set)
                     (difference all-keys-set ref-key-set)))))


(defn pull-entities [db eids]
  (map first
       (d/q '[:find (pull ?e [*])
              :in $ [?e ...]]
            db eids)))

(defn pull-entities-filtered [db attr]
  (d/index-pull db {:index :avet
                    :selector [attr]
                    :start [attr]}))

(defn walk-entity
  ([db entity-map]
   (walk-entity db [] entity-map))
  ([db parents entity-map]
   (let [eid (:db/id entity-map)]
     (assert (some? eid) entity-map)
     (assert (number? eid) eid)
     ;; (println "parents" parents)
     {:fields (field-names entity-map)
      :parents (join ";" parents)
      :children
      (doall (for [[key val] entity-map
                   :when (and (vector? val)
                              (-> val seq first :db/id)
                              (not (key descend-blacklist))
                              #_(< (count parents) 3))]
               (let [eids (keep :db/id (filter map? val))
                     child-parents (conj parents key)]

                 [key (mapv (partial walk-entity db child-parents) (pull-entities db eids))])))})))

(defn walk-db [db root-attr]
  (let [root-ents (mapv first (d/q [:find '(pull ?e [*]) :where ['?e root-attr]] db))]
    ;; (println (first root-ents))
    (mapv (partial walk-entity db) root-ents)))

(defn flatten-db [name m]
  (assert (map? m) m)
  (let [parents (:parents m)
        field-rows (for [f (:fields m)]
                     (do
                       ; (println "parents" parents-text)
                       [:entity name
                                        ; :parents parents-text
                        :parents parents
                        :field f]))
        child-rows (doall (for [child (:children m)]
                            (do
                              (assert (vector? child))
                                        ; (println (count child))
                              (let [[child-name child-refs] child]
                                (mapv (partial flatten-db child-name) child-refs)))))]
    (conj field-rows child-rows)))


(defn unique-sorted [x]

  (sort (fn [a b]
          (compare (str a) (str b))) (into #{} x)))

(def pre-split-columns ["entity" "parents" "attribute"])
(def post-split-columns ["entity" "attribute"  "parent1" "parent2" "parent3" "parent4" "parent5" "parent6" "parent7" "parent8" "parent9" "parent10"])

(defn make-csv-rows [flat-data]
  (mapv (juxt (comp str second) (comp str #(nth % 3)) (comp str last))
        flat-data))

(defn split-parents [rows]
  ;; splice the parents to additional columns, from [:myattr [:parent1 :parent2 ...] :myentity] into [:myentity :myattr :parent1 :parent2 ...]

  (for [r rows]
    (concat [(get r 0) (get r 2)]
            (split (get r 1) #";"))))

(defn output-csv [flat-data out-csv-name]
  (->> flat-data
       make-csv-rows
       split-parents
       unique-sorted
       (concat [post-split-columns])
       (csv/write-csv *out*)
       (with-out-str)
       (spit out-csv-name)))

(defn make-flat-csv-for-db [db root-entity-key out-csv-name root-parent-name]
  (let [;; entity-tree (take 1 (walk-db db root-entity-key))
        entity-tree (walk-db db root-entity-key)
        flattened (mapcat #(partition (* 2 (count pre-split-columns))
                                      (flatten (flatten-db root-parent-name %)))
                          entity-tree)]
    (output-csv flattened out-csv-name)))

(defn -main [& args]
  (make-flat-csv-for-db (d/db main-conn) :thk.project/id "main-db.csv" :projects)
  (make-flat-csv-for-db (d/db asset-conn) :asset/project "asset-db.csv" :asset/project))

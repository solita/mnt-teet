(ns generate-schema
  (:require [clojure.string :as s]
            [datomic.client.api :as d]
            [clojure.data.csv :as csv]
            [clojure.set :refer [difference]]))

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
    (into [] (concat (map #(str "ref: " (name %1))
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
   (walk-entity db 1 (atom #{}) entity-map))
  ([db depth seen-atom entity-map]
   (let [eid (:db/id entity-map)]
     (assert (some? eid) entity-map)
     (assert (number? eid) eid)
     (swap! seen-atom conj eid)
     {:fields (field-names entity-map)
      :children
      (doall (for [[key val] entity-map
                   :when (and (vector? val) (-> val seq first :db/id))]
               (let [eids (keep :db/id (filter map? val))
                     seen (deref seen-atom)
                     new-eids (filter (complement seen) eids)]
                 (println "depth" depth "- child eids" eids "from" val)
                 (do (assert  (every? number? eids))
                     [key (mapv (partial walk-entity db (inc depth) seen-atom) (pull-entities db new-eids))]))))})))



(defn walk-db [db root-attr]
  (let [root-ents (mapv first (d/q [:find '(pull ?e [*]) :where ['?e root-attr]] db))]
    (println (first root-ents))
    (mapv (partial walk-entity db) root-ents)))

(defn flatten-db [name m]
  (assert (map? m) m)
  (let [field-rows (for [f (:fields m)]
                     [:entity name :field f])
        child-rows (doall (for [child (:children m)]
                            (do
                              (assert (vector? child))
                                        ; (println (count child))
                              (let [[child-name child-refs] child]
                                (mapv (partial flatten-db child-name) child-refs)))))]
    (conj field-rows child-rows)))


(defn unique-sorted [x]
  (sort (into #{} x)))

(defn make-csv-rows [flat-data]
  (concat [["entity" "attribute"]]
          (mapv (juxt (comp name second) (comp name last))
                flat-data)))


(defn output-csv [flat-data out-csv-name]
  (->> flat-data
       make-csv-rows
       unique-sorted
       (csv/write-csv *out*)
       (with-out-str)
       (spit out-csv-name)))

(defn make-flat-csv-for-db [db root-entity-key out-csv-name]
  (let [entity-tree (walk-db db root-entity-key)
        flattened (mapcat #(partition 4 (flatten (flatten-db :projects %))) entity-tree)]
    (output-csv flattened out-csv-name)))


(defn -main [& args]
  (make-flat-csv-for-db (d/db main-conn) :thk.project/id "main-db.csv")
  (make-flat-csv-for-db (d/db asset-conn) :asset/project "asset-db.csv"))

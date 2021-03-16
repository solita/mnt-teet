(ns teet.asset.asset-db
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]))

(def ctype-pattern
  '[*
    {:ctype/_parent [*]}
    {:attribute/_parent
     [*
      {:enum/_attribute [*]}]}])

(def type-library-pattern
  `[~'*
    {:fclass/_fgroup
     [~'*
      {:attribute/_parent [~'* {:enum/_attribute [~'*]}]}
      {:ctype/_parent ~ctype-pattern}]}])

(defn- ctype? [x]
  (and (map? x)
       (du/enum= (:asset-schema/type x) :asset-schema.type/ctype)))

(defn- child-ctypes [db ctype]
  (update ctype :ctype/_parent
          #(->> %
                (mapv
                 (fn [child]
                   (if (ctype? child)
                     (d/pull db ctype-pattern (:db/id child))
                     child))))))

(defn asset-type-library [db]
  {:ctype/common (d/pull db ctype-pattern :ctype/common)
   :fgroups
   (walk/postwalk
    (fn [x]
      (if (ctype? x)
        (child-ctypes db x)
        x))

    (mapv first
          (d/q '[:find (pull ?fg p)
                 :where [?fg :asset-schema/type :asset-schema.type/fgroup]
                 :in $ p]
               db type-library-pattern)))})


(defn project-cost-items
  "Pull all assets for the given project. Group by fgroup/fclass."
  [adb thk-project-id]
  (->> (d/q '[:find (pull ?a [:db/id :common/name :common/status
                              {:asset/fclass [:db/ident :asset-schema/label
                                              {:fclass/fgroup [:db/ident :asset-schema/label]}]}])
              :where
              [?a :asset/project ?p]
              [?a :asset/fclass _]

              :in $ ?p]
            adb thk-project-id)

       (map first)
       ;; Group by fgroup
       (group-by #(get-in % [:asset/fclass :fclass/fgroup]))

       ;; For each fgroup, further group assets by fclass
       (cu/map-vals (fn [cost-items-for-fgroup]
                      (group-by #(-> % :asset/fclass (dissoc :fclass/fgroup))
                                cost-items-for-fgroup)))))

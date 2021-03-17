(ns teet.asset.asset-type-library
  "Code for handling asset type library and generated forms data."
  (:require [teet.util.collection :as cu]
            [clojure.walk :as walk]))

(defn rotl-map
  "Return a flat mapping of all ROTL items, by :db/ident."
  [rotl]
  (into {}
        (map (juxt :db/ident identity))

        ;; collect maps that have :db/ident and more fields apart from identity
        (cu/collect #(and (map? %)
                          (contains? % :db/ident)
                          (seq (dissoc % :db/id :db/ident)))
                    rotl)))

#?(:clj
   (defn form->db
     "Prepare data from asset form to be saved in the database"
     [rotl form-data]
     (walk/prewalk
      (fn [x]
        (if-let [attr (and (map-entry? x)
                           (get rotl (first x)))]
          (case (get-in attr [:db/valueType :db/ident])
            :db.type/bigdec
            (update x 1 bigdec)

            :db.type/long
            (update x 1 #(Long/parseLong %))

            ;; No parsing
            x)
          x))
      form-data)))

#?(:clj
   (defn db->form
     "Prepare asset data fetched from db for frontend form"
     [asset]
     (walk/prewalk
      (fn [x]
        (cond
          (and (map? x)
               (= #{:db/id :db/ident}
                  (into #{} (keys x))))
          (:db/ident x)

          (decimal? x)
          (str x)

          :else
          x))
      asset)))

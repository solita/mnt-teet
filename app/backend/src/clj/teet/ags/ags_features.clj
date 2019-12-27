(ns teet.ags.ags-features
  "Create WKT features from parsed AGS data."
  (:require [clojure.string :as str]))

(defn- json-property-key [kw]
  (cond
    ;; Output :LOCA/ID as the "id" property
    (= kw :LOCA/ID)
    "id"

    ;; Skip other groups that refer to the same id
    (= "LOCA_ID" (name kw))
    nil


    ;; Output all other keys as properties
    :else
    (str (str/lower-case (namespace kw)) "_" (str/lower-case (name kw)))))

(defn- blank? [x]
  (or (nil? x)
      (and (string? x) (str/blank? x))))

(defn- json-properties [props]
  (reduce-kv (fn [props k v]
               (if-let [json-key (json-property-key k)]
                 (if (blank? v)
                   props
                   (assoc props json-key v))
                 props))
             {}
             props))


(defn ags-features [ags-groups]
  (let [group-data (into {}
                         (map (juxt :group-name :data))
                         ags-groups)
        non-location-groups (disj (into #{} (keys group-data)) "LOCA")
        group-data-by-location-id (memoize
                                   (fn [group-name]
                                     (into {}
                                           (map (juxt (keyword group-name "LOCA_ID") identity))
                                           (group-data group-name))))]
    (for [{id :LOCA/ID
           x :LOCA/NATE
           y :LOCA/NATN :as loca} (group-data "LOCA")
          :let [props (json-properties
                       (reduce (fn [props group-name]
                                 (merge props
                                        ((group-data-by-location-id group-name) id)))
                               loca
                               non-location-groups))]]
      {:id (props "id")
       :geometry (str "POINT(" x " " y ")")
       :properties props})))

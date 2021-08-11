(ns teet.asset.asset-queries
  (:require [datomic.client.api :as d]
            [ring.util.io :as ring-io]
            [teet.asset.asset-boq :as asset-boq]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.db-api.core :as db-api :refer [defquery]]
            [teet.environment :as environment]
            [teet.localization :as localization :refer [with-language tr tr-enum]]
            [teet.log :as log]
            [teet.project.project-db :as project-db]
            [teet.road.road-query :as road-query]
            [teet.transit :as transit]
            [teet.user.user-db :as user-db]
            [teet.util.euro :as euro]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [teet.util.coerce :refer [->long ->bigdec]]
            [teet.util.collection :as cu]
            [clojure.set :as set]
            [teet.util.geo :as geo]
            [jeesql.core :as jeesql]))

(jeesql/defqueries "teet/asset/asset_queries.sql")

(defquery :asset/type-library
  {:doc "Query the asset types"
   :context {adb :asset-db}
   :unauthenticated? true
   :args _
   :last-modified (asset-db/last-atl-modification-time adb)}
  (asset-db/asset-type-library adb))

(defn- fetch-cost-item [adb oid]
  (asset-type-library/db->form
   (asset-type-library/rotl-map
    (asset-db/asset-type-library adb))
   (d/pull adb '[*] [:asset/oid oid])))

(def ^:private relevant-roads-buffer-meters 50)

(defn- fclass-and-fgroup-totals
  "Calculate subtotals for each fgroup and fclass."
  [atl cost-groups]
  (reduce
   (fn [totals {:keys [type total-cost]}]
     (let [[{fgroup :db/ident}
            {fclass :db/ident} & _]
           (asset-type-library/type-hierarchy atl type)
           add-by (fn [v by]
                    (+ (or v 0M)
                       (or by 0M)))]
       (-> totals
           (update fgroup add-by total-cost)
           (update fclass add-by total-cost))))
   {}
   cost-groups))

(defquery :asset/project-relevant-roads
  {:doc "Query project's relevant roads"
   :context {:keys [db]}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]}
  ;; Fetch relevant roads for cost items of project, meaning the
  ;; project road along with roads intersecting project geometry
  ;; with a buffer of 50 meters.
  ;;
  ;; This can take some time, so it should be fetched once.
  ;; Could be cached per project as well.
  (let [integration-id (project-db/thk-id->integration-id-uuid db project-id)
        ctx (environment/config-map {:api-url [:api-url]
                                     :api-secret [:auth :jwt-secret]
                                     :wfs-url [:road-registry :wfs-url]})]
    (road-query/fetch-relevant-roads-for-project-cost-items
     ctx
     integration-id
     relevant-roads-buffer-meters)))

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db] adb :asset-db}
   :args {project-id :thk.project/id
          cost-item :cost-item
          cost-totals :cost-totals
          materials-and-products :materials-and-products
          road :road}
   :project-id [:thk.project/id project-id]}
  (let [atl (asset-db/asset-type-library adb)]
    (transit/with-write-options
      euro/transit-type-handlers
      (merge
       {:cost-items (asset-db/project-cost-items adb project-id)
        :version (asset-db/project-boq-version adb project-id)
        :version-history (into [] (asset-db/project-boq-version-history adb project-id))
        :latest-change (when-let [[timestamp author] (asset-db/latest-change-in-project adb project-id)]
                         {:user (user-db/user-display-info db [:user/id author])
                          :timestamp timestamp})
        :project (project-db/project-by-id db [:thk.project/id project-id])}
       (when cost-totals
         (let [cost-groups (asset-db/project-cost-groups-totals
                            adb project-id
                            (when road
                              (cond
                                (= road "all-cost-items")
                                (asset-db/project-assets-and-components adb project-id)

                                (= road "no-road-reference")
                                (asset-db/project-assets-and-components-without-road adb project-id)
                                :else
                                (asset-db/project-assets-and-components-matching-road
                                 adb project-id (->long road)))))]
           {:cost-totals
            {:cost-groups cost-groups
             :fclass-and-fgroup-totals (fclass-and-fgroup-totals atl cost-groups)
             :total-cost (reduce + (keep :total-cost cost-groups))}}))
       (when cost-item
         {:cost-item (fetch-cost-item
                      adb
                      ;; Always pull the full asset even when focusing on a
                      ;; specific subcomponent.
                      ;; PENDING: what if there are thousands?
                      (cond (asset-model/component-oid? cost-item)
                            (asset-model/component-asset-oid cost-item)

                            (asset-model/material-oid? cost-item)
                            (asset-model/material-asset-oid cost-item)

                            :else
                            cost-item))})
       (when materials-and-products
         {:materials-and-products
          (asset-db/project-materials-totals adb project-id atl)})))))

(s/def :boq-export/version integer?)
(s/def :boq-export/unit-prices? boolean?)
(s/def :boq-export/language keyword?)

(defquery :asset/export-boq
  {:doc "Export Bill of Quantities Excel for the project"
   :spec (s/keys :req [:thk.project/id :boq-export/unit-prices? :boq-export/language]
                 :opt [:boq-export/version])
   :context {:keys [db] adb :asset-db}
   :args {project-id :thk.project/id
          :boq-export/keys [version unit-prices? language]}
   :project-id [:thk.project/id project-id]}
  (with-language language
    (let [version-info (when version
                         (d/pull adb '[*] version))
          adb (if version
                (asset-db/version-db adb version)
                adb)
          filename (tr [:asset :export-boq-filename]
                       {:project project-id
                        :version (if version-info
                                   (str (tr-enum (:boq-version/type version-info))
                                        "-v"
                                        (:boq-version/number version-info))
                                   (str/lower-case
                                    (tr [:asset :unofficial-version])))})]
      ^{:format :raw}
      {:status 200
       :headers {"Content-Disposition"
                 (str "attachment; filename=" filename)}
       :body (let [atl (asset-db/asset-type-library adb)
                   cost-groups (asset-db/project-cost-groups-totals adb project-id)]
               (ring-io/piped-input-stream
                (fn [out]
                  (try
                    (asset-boq/export-boq
                     out
                     {:atl atl
                      :cost-groups cost-groups
                      :include-unit-prices? unit-prices?
                      :version version-info
                      :project-id project-id
                      :language language
                      :project-name (project-db/project-name db [:thk.project/id project-id])})
                    (catch Throwable t
                      (log/error t "Exception generating BOQ excel"
                                 {:project-id project-id}))))))})))

(defquery :asset/version-history
  {:doc "Query version history for BOQ"
   :spec (s/keys :req [:thk.project/id])
   :context {adb :asset-db}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]}
  (asset-db/project-boq-version-history adb project-id))


(s/def :assets-search/fclass (s/coll-of keyword?))

(def ^:private result-count-limit 1000)

(defn- e=
  "return set of :db/id values of entities that have
  the exact value for attribute"
  [db attr value]
  (into #{}
        (map :e)
        (d/datoms db {:index :avet
                      :limit -1
                      :components [attr value]})))

(defn- assets-only [db matching-entities]
  (reduce disj
          matching-entities
          (map first (d/q '[:find ?e
                            :where [(missing? $ ?e :asset/fclass)]
                            :in $ [?e ...]] db matching-entities))))

(defn- bbq
  "Return set of :db/id value sof entities whose location
  start-point or end-point is within the bounding box."
  ([db xmin ymin xmax ymax]
   (bbq db xmin ymin xmax ymax (constantly true)))
  ([db xmin ymin xmax ymax point-filter-fn]
   (let [entities-within-range
         (comp
          (take-while (fn [{[x _y] :v}]
                        (<= x xmax)))
          (filter (fn [{[_x y] :v}]
                    (<= ymin y ymax)))
          (filter point-filter-fn)
          (map :e))

         start-within
         (into #{}
               entities-within-range
               (d/index-range db {:attrid :location/start-point
                                  :start [xmin ymin]
                                  :limit -1}))

         end-within
         (into #{}
               entities-within-range
               (d/index-range db {:attrid :location/end-point
                                  :start [xmin ymin]
                                  :limit -1}))]
     (assets-only
      db
      (set/union start-within end-within)))))

(defn fclass=
  "Find entities belonging to a single fclass"
  [db fclass]
  (e= db :asset/fclass fclass))

(defmulti search-by (fn [_db _sql-conn key _value] key))

(defmethod search-by :fclass [db _ _ fclasses]
  (apply set/union
         (map #(fclass= db %)
              fclasses)))

(defmethod search-by :common/status [db _ _ statuses]
  (assets-only
   db
   (into #{}
         (mapcat #(e= db :common/status %))
         statuses)))

(defmethod search-by :bbox [db _ _ [xmin ymin xmax ymax]]
  (bbq db xmin ymin xmax ymax))

(defmethod search-by :current-location [db _ _ [x y radius]]
  (bbq db (- x radius) (- y radius) (+ x radius) (+ y radius)
       (fn [{point :v}]
         (<= (geo/distance point [x y]) radius))))

(defn search-by-road-address [db {:location/keys [road-nr carriageway start-km end-km]}]
  (into #{}
        (map first)
        (d/q {:query
              (vec
               (concat
                '[:find ?e
                  :where
                  [?e :location/road-nr ?road-nr]
                  [?e :location/carriageway ?carriageway]]
                (when (or start-km end-km)
                  '[[?e :location/start-km ?start]])
                (when start-km
                  '[[(>= ?start ?start-km)]])
                (when end-km
                  ;; If asset has no end km use the start km (single point)
                  '[[(get-else $ ?e :location/end-km ?start) ?end]
                    [(<= ?end ?end-km)]])
                '[[?e :asset/fclass _]]
                '[:in $ ?road-nr ?carriageway]
                (when start-km '[?start-km])
                (when end-km '[?end-km])))
              :args (remove nil? [db road-nr carriageway
                                  (some-> start-km ->bigdec)
                                  (some-> end-km ->bigdec)])})))

(defmethod search-by :road-address [db _ _ addrs]
  (apply set/union
         (map (partial search-by-road-address db)
              addrs)))

(defmethod search-by :region [db sql-conn _ regions]
  (loop [acc #{}
         [r & regions] regions]
    (if-not r
      ;; Resolve OIDs to db ids
      (into #{}
            (map first)
            (d/q '[:find ?e
                   :where [?e :asset/oid ?oid]
                   :in $ [?oid ...]]
                 db acc))
      (let [[ds id] (str/split r #":")
            g (fetch-feature-geometry-by-datasource-and-id
               sql-conn
               {:datasource (Integer/parseInt ds)
                :id id})]
        (recur (into acc
                     (map :oid (fetch-assets-intersecting-geometry
                                sql-conn
                                {:geometry g})))
               regions)))))

(defn- search-by-map [db sql-conn criteria-map]
  (reduce-kv (fn [acc by val]
               (let [result (search-by db sql-conn by val)]
                 (if (nil? acc)
                   result
                   (set/intersection acc result))))
             nil
             criteria-map))



(defquery :assets/search
  {:doc "Search assets based on multiple criteria. Returns assets as listing and a GeoJSON feature collection."
   :spec (s/keys :opt-un [:assets-search/fclass])
   :args search-criteria
   :context {adb :asset-db
             sql-conn :sql-conn}
   :allowed-for-all-users? true}
  (let [ids (take (inc result-count-limit)
                  (search-by-map adb sql-conn search-criteria))
        more-results? (> (count ids) result-count-limit)
        assets
        (map first
             (d/qseq '[:find (pull ?a [:asset/fclass :common/status :asset/oid
                                       :location/road-nr :location/carriageway
                                       :location/start-km :location/end-km
                                       :location/start-point :location/end-point])
                       :in $ [?a ...]]
                     adb (take result-count-limit ids)))]
    {:more-results? more-results?
     :result-count-limit result-count-limit
     :assets (mapv #(-> %
                        (dissoc :location/start-point :location/end-point)
                        (cu/update-in-if-exists [:location/start-km] asset-model/format-location-km)
                        (cu/update-in-if-exists [:location/end-km] asset-model/format-location-km))
                   assets)
     :geojson (cheshire/encode
               {:type "FeatureCollection"
                :features
                (for [{:location/keys [start-point end-point] :as a} assets
                      :when (and start-point end-point)]
                  {:type "Feature"
                   :properties {"oid" (:asset/oid a)
                                "fclass" (:db/ident (:asset/fclass a))}
                   :geometry {:type "LineString"
                              :coordinates [start-point end-point]}})})}))

(defquery :assets/geojson
  {:doc "Return GeoJSON for assets found by search."
   :spec (s/keys :opt-un [:assets-search/fclass])
   :args criteria
   :context {adb :asset-db
             sql-conn :sql-conn}
   :allowed-for-all-users? true}
  (let [criteria (update criteria :bbox #(mapv bigdec %))
        assets
        (map first
             (d/qseq '[:find (pull ?a [:asset/fclass :asset/oid
                                       :location/start-point :location/end-point])
                       :in $ [?a ...]]
                     adb
                     (search-by-map adb sql-conn criteria)))]
    ^{:format :raw}
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            {:type "FeatureCollection"
             :features
             (for [{:location/keys [start-point end-point] :as a} assets
                   :when (and start-point end-point)]
               {:type "Feature"
                :properties {"oid" (:asset/oid a)
                             "fclass" (:db/ident (:asset/fclass a))}
                :geometry {:type "LineString"
                           :coordinates [start-point end-point]}})})}))

(defquery :assets/details
  {:doc "Fetch one asset for details view"
   :context {adb :asset-db}
   :args {oid :asset/oid}
   :allowed-for-all-users? true}
  (let [oid (cond
              (asset-model/material-oid? oid)
              (asset-model/material-asset-oid oid)

              (asset-model/component-oid? oid)
              (asset-model/component-asset-oid oid)

              :else oid)]
    (asset-type-library/db->form
     (asset-type-library/rotl-map
      (asset-db/asset-type-library adb))
     (d/pull adb '[*] [:asset/oid oid]))))

(defquery :asset/geometries
  {:doc "Return GeoJSON for asset and components by asset id. This is for showing
the parent asset and sibling components on the map when creating a new component."
   :spec (s/keys :req [:asset/oid])
   :args {oid :asset/oid
          except :except
          language :language
          :or {except #{}
               language :et}}
   :context {adb :asset-db}
   :allowed-for-all-users? true}
  (let [oid (if (asset-model/component-oid? oid)
              (asset-model/component-asset-oid oid)
              oid)
        oids (into []
                   (remove except)
                   (concat [oid]
                           (asset-db/asset-component-oids adb oid)))
        assets
        (map first
             (d/qseq '[:find (pull ?a [:asset/fclass :component/ctype :asset/oid
                                       :location/start-point :location/end-point])
                       :where [?a :asset/oid ?oid]
                       :in $ [?oid ...]]
                     adb oids))
        atl (asset-db/asset-type-library adb)
        feature-tooltip (fn [oid type]
                          (str oid " "
                               (asset-type-library/label
                                language
                                (asset-type-library/item-by-ident atl type))))]
    ^{:format :raw}
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            {:type "FeatureCollection"
             :features
             (for [{:location/keys [start-point end-point] :as a} assets
                   :when start-point]
               {:type "Feature"
                :properties (merge {"oid" (:asset/oid a)}
                                   (when-let [fc (some-> a :asset/fclass :db/ident)]
                                     {:fclass fc
                                      :tooltip (feature-tooltip (:asset/oid a) fc)})
                                   (when-let [ct (some-> a :component/ctype :db/ident)]
                                     {:ctype ct
                                      :tooltip (feature-tooltip (:asset/oid a) ct)}))
                :geometry (if end-point
                            {:type "LineString"
                             :coordinates [start-point end-point]}
                            {:type "Point"
                             :coordinates start-point})})})}))

(defquery :assets/regions
  {:doc "Fetch counties/municipalities list for selection."
   :args _
   :context {c :sql-conn}
   :allowed-for-all-users? true}
  (let [region-ds (map :id (fetch-region-ds c))
        areas (fetch-regions c {:ds region-ds})
        counties (into []
                       (filter (complement :okood))
                       (sort-by :label areas))
        municipalities-by-county (group-by :mkood
                                           (filter :okood areas))]
    (mapv
     (fn [county]
       (-> county
           (select-keys [:id :label])
           (assoc :municipalities
                  (into []
                        (map #(select-keys % [:id :label]))
                        (sort-by :label (municipalities-by-county (:mkood county)))))))
     counties)))

(defquery :assets/regions-geojson
  {:doc "Fetch GeoJSON for selected regions"
   :args {regions :regions}
   :context {c :sql-conn}
   :allowed-for-all-users? true}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (fetch-regions-geojson-by-ids c {:ids (into-array String regions)})})

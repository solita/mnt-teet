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
            [clojure.string :as str]))

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

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id
          cost-item :cost-item
          cost-totals :cost-totals
          relevant-roads :relevant-roads
          road :road}
   :project-id [:thk.project/id project-id]
   ;; fixme: cost items authz
   :authorization {:project/read-info {}}}
  (let [atl (asset-db/asset-type-library adb)]
    (transit/with-write-options
      euro/transit-type-handlers
      (merge
       {:asset-type-library atl
        :cost-items (asset-db/project-cost-items adb project-id)
        :version (asset-db/project-boq-version adb project-id)
        :latest-change (when-let [[timestamp author] (asset-db/latest-change-in-project adb project-id)]
                         {:user (user-db/user-display-info db [:user/id author])
                          :timestamp timestamp})
        :project (project-db/project-by-id db [:thk.project/id project-id])}
       (when cost-totals
         (let [cost-groups (asset-db/project-cost-groups-totals
                            adb project-id
                            (when road
                              (asset-db/project-assets-and-components-matching-road adb project-id road)))]
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
                      (if (asset-model/component-oid? cost-item)
                        (asset-model/component-asset-oid cost-item)
                        cost-item))})
       ;; Fetch relevant roads for cost items of project, meaning the
       ;; project road along with roads intersecting project geometry
       ;; with a buffer of 50 meters.
       (when relevant-roads
         {:relevant-roads
          (when-let [integration-id (project-db/thk-id->integration-id-uuid db project-id)]
            (let [ctx (environment/config-map {:api-url [:api-url]
                                               :api-secret [:auth :jwt-secret]
                                               :wfs-url [:road-registry :wfs-url]})]
              (road-query/fetch-relevant-roads-for-project-cost-items ctx
                                                                      integration-id
                                                                      relevant-roads-buffer-meters)))})))))

(s/def :boq-export/version integer?)
(s/def :boq-export/unit-prices? boolean?)
(s/def :boq-export/language keyword?)

(defquery :asset/export-boq
  {:doc "Export Bill of Quantities Excel for the project"
   :spec (s/keys :req [:thk.project/id :boq-export/unit-prices? :boq-export/language]
                 :opt [:boq-export/version])
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id
          :boq-export/keys [version unit-prices? language]}
   :project-id [:thk.project/id project-id]
   :authorization {:project/read-info {}}}
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
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:project/read-info {}}}
  (asset-db/project-boq-version-history adb project-id))

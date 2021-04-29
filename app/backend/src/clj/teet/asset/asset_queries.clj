(ns teet.asset.asset-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.project.project-db :as project-db]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-model :as asset-model]
            [teet.util.euro :as euro]
            [teet.transit :as transit]
            [ring.util.io :as ring-io]
            [teet.asset.asset-boq :as asset-boq]
            [teet.localization :as localization :refer [with-language tr tr-enum]]
            [teet.log :as log]
            [teet.user.user-db :as user-db]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defquery :asset/type-library
  {:doc "Query the asset types"
   :context _
   :unauthenticated? true
   :args _}
  (asset-db/asset-type-library (environment/asset-db)))

(defn- fetch-cost-item [adb oid]
  (asset-type-library/db->form
   (asset-type-library/rotl-map
    (asset-db/asset-type-library adb))
   (d/pull adb '[*] [:asset/oid oid])))

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id
          cost-item :cost-item
          cost-totals :cost-totals}
   :project-id [:thk.project/id project-id]
   ;; fixme: cost items authz
   :authorization {:project/read-info {}}}
  (transit/with-write-options
    euro/transit-type-handlers
    (merge
     {:asset-type-library (asset-db/asset-type-library adb)
      :cost-items (asset-db/project-cost-items adb project-id)
      :version (asset-db/project-boq-version adb project-id)
      :latest-change (when-let [[timestamp author] (asset-db/latest-change-in-project adb project-id)]
                       {:user (user-db/user-display-info db [:user/id author])
                        :timestamp timestamp})
      :project (project-db/project-by-id db [:thk.project/id project-id])}
     (when cost-totals
       (let [cost-groups (asset-db/project-cost-groups-totals adb project-id)]
         {:cost-totals
          {:cost-groups cost-groups
           :total-cost (reduce + (keep :total-cost cost-groups))}}))
     (when cost-item
       {:cost-item (fetch-cost-item
                    adb
                    ;; Always pull the full asset even when focusing on a
                    ;; specific subcomponent.
                    ;; PENDING: what if there are thousands?
                    (if (asset-model/component-oid? cost-item)
                      (asset-model/component-asset-oid cost-item)
                      cost-item))}))))

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

(ns teet.admin.index-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.transit :as transit]
            [teet.util.euro :as euro])
  (:import (java.util UUID)))

(defn- get-index-data
  "Returns the indexes data"
  [db]
  )

(defn- with-bigdec-format [x]
  (transit/with-write-options
    {java.math.BigDecimal euro/format-no-sign}
    x))

(def index-listing-attributes
  [:db/id
   :cost-index/name
   :cost-index/type
   :cost-index/unit
   :cost-index/valid-from
   {:cost-index/values
    [:db/id
     :index-value/month
     :index-value/year
     :index-value/value
     ]}])

(defquery :admin/indexes-data
          {:doc "Pull information about the indexes"
           :context {:keys [db] :as ctx}
           :args {}
           :project-id nil
           :authorization {:admin/manage-indexes {}}}
          (with-bigdec-format
            {:index-data
              (->> (d/q '[:find (pull ?e columns)
                          :in $ columns
                          :where [?e :cost-index/name _]]
                        db index-listing-attributes)
                   (mapv first))}))
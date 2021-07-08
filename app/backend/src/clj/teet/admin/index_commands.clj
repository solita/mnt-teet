(ns teet.admin.index-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]
            [datomic.client.api :as d]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [teet.util.coerce :as cc]
            [teet.util.date :as dt]))

(defn- by-month [start-date]
  (iterate #(t/plus % (t/months 1)) start-date))

(defn- index-value-id
  [db index-id year month]
  (ffirst (d/q '[:find (pull ?vals [:db/id])
                 :where
                 [?i :cost-index/values ?vals]
                 [?vals :index-value/year ?y]
                 [?vals :index-value/month ?m]
                 :in $ ?i ?y ?m]
               db index-id year month)))

(defn- edit-index-values-tx
  [db index-id index-data]
  (let [index-vals (d/pull db [:cost-index/valid-from :cost-index/values] index-id)
        valid-from (c/from-date (:cost-index/valid-from index-vals))
        values (:cost-index/values index-vals)
        month-objects (take
                        (t/in-months
                          (t/interval
                            valid-from
                            (t/now)))
                        (by-month valid-from))
        last-month (t/minus (t/now) (t/months 1))
        last-month-kw (str "index-value-" (t/year last-month) "-" (t/month last-month))]
    (if values
      (let [last-month-id (:db/id (index-value-id db index-id (t/year last-month) (t/month last-month)))
            last-month-value ((keyword last-month-kw) index-data)
            _ (println "Updating index value: " last-month-kw last-month-id last-month-value)]
        (if (nil? last-month-id)
          {:db/id index-id
           :cost-index/values
           {:index-value/year (t/year last-month)
            :index-value/month (t/month last-month)
            :index-value/value (bigdec last-month-value)}}
          {:db/id last-month-id
         :index-value/value (bigdec last-month-value)}))                                                    ; In case values are there, update only last month
      {:db/id index-id
       :cost-index/values
       (mapv (fn [cur-month]
              (let [month-kw (str "index-value-" (t/year cur-month) "-" (t/month cur-month))]
                {:index-value/year (t/year cur-month)
                 :index-value/month (t/month cur-month)
                 :index-value/value (bigdec ((keyword month-kw) index-data))})) month-objects)}))
  )

(defcommand :index/add-index
  {:doc "Add new index"
   :context {:keys [user db]}
   :payload index-data
   :project-id nil
   :audit? true
   :authorization {:admin/manage-indexes {}}
   :transact [(merge {:cost-index/name (:cost-index/name index-data)
                      :cost-index/type (:cost-index/type index-data)
                      :cost-index/valid-from (dt/->date (t/year (c/from-date (:cost-index/valid-from index-data)))
                                                        (t/month (c/from-date (:cost-index/valid-from index-data)))
                                                        1)}
                     (meta-model/creation-meta user))]})

(defcommand :index/edit-index
  {:doc "Edit index details (name)"
   :context {:keys [user db]}
   :payload index-data
   :project-id nil
   :audit? true
   :authorization {:admin/manage-indexes {}}
   :transact (let [index-id (cc/->long (:index-id index-data))]
               [{:db/id index-id
                 :cost-index/name (:cost-index/name index-data)}])})

(defcommand :index/delete-index
  {:doc "Remove the index"
   :context {:keys [user db]}
   :payload index-data
   :project-id nil
   :audit? true
   :authorization {:admin/manage-indexes {}}
   :transact (let [index-id (cc/->long (:index-id index-data))]
               [[:db/retractEntity index-id]])})

(defcommand :index/edit-index-values
  {:doc "Edit index details (name)"
   :context {:keys [user db]}
   :payload index-data
   :project-id nil
   :audit? true
   :authorization {:admin/manage-indexes {}}
   :transact (let [index-id (cc/->long (:index-id index-data))]
               [(edit-index-values-tx db index-id index-data)])})
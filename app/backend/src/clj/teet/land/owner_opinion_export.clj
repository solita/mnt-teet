(ns teet.land.owner-opinion-export
  (:require [teet.localization :refer [tr tr-enum with-language]]
            [datomic.client.api :as d]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.util.html-export :as html-export-util]))

(defn- opinions-by-type
  [db activity type]
  (->>
    (d/q '[:find (pull ?opinion [*])
           :where
           [?opinion :land-owner-opinion/activity ?activity]
           [?opinion :land-owner-opinion/type ?type]
           [(missing? $ ?opinion :meta/deleted?)]
           :in $ ?activity ?type]
         db activity type)
    (db-api-large-text/with-large-text #{:cooperation.response/content :cooperation.opinion/comment})
    (map first)))

(defn summary-table [db activity type]
  (let [opinions (opinions-by-type db activity type)]
    (html-export-util/html-export-helper
      {:title (str (tr-enum type) " land owner opinions")
       :content [:div#export
                 [:h1 "This is the first work in progress version"]
                 [:p (pr-str opinions)]]})))

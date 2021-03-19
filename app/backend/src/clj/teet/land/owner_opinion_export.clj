(ns teet.land.owner-opinion-export
  (:require [teet.util.collection :as cu]
            [hiccup.core :as h]
            [teet.localization :refer [tr tr-enum with-language]]
            [datomic.client.api :as d]
            [teet.db-api.db-api-large-text :as db-api-large-text]))

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
    (h/html
      (with-language :et
                     (cu/eager
                       [:html
                        [:head
                         [:title (tr-enum type)]
                         [:script
                          (str "function copyToClipboard() {"
                               "var e = document.getElementById('export');"
                               "var s = window.getSelection();"
                               "var r = document.createRange();"
                               "r.selectNodeContents(e);"
                               "s.removeAllRanges();"
                               "s.addRange(r);"
                               "document.execCommand('Copy');"
                               "}")]]
                        [:body
                         [:button {:style "float: right;"
                                   :onclick "copyToClipboard()"}
                          (tr [:buttons :copy-to-clipboard])]
                         [:div#export
                          [:h1 "This is the first work in progress version"]
                          [:p (pr-str opinions)]]]])))))

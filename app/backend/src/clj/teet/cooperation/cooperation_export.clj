(ns teet.cooperation.cooperation-export
  "Export cooperation summary table as HTML"
  (:require [datomic.client.api :as d]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.util.collection :as cu]
            [hiccup.core :as h]
            [teet.localization :refer [tr tr-enum with-language]]
            [teet.util.date :as date]
            [teet.util.md :as md]
            [teet.project.project-db :as project-db]))

(defn- applications-by-response-type [db activity type]
  (->>
   (d/q '[:find (pull ?a [*
                          {:cooperation.3rd-party/_applications
                           [:cooperation.3rd-party/name]}
                          {:cooperation.application/response
                           [*]}
                          {:cooperation.application/opinion
                           [*]}])
          :where
          [?a :cooperation.application/activity ?activity]
          [?a :cooperation.application/type ?type]
          [(missing? $ ?a :meta/deleted?)]

          :in $ ?activity type] db activity type)
   (db-api-large-text/with-large-text #{:cooperation.response/content :cooperation.opinion/comment})
   (map first)
   (group-by :cooperation.application/response-type)
   (cu/map-keys :db/ident)))

(defn- numbered [items render-item-fn]
  (for [{i ::cu/i :as item} items]
    [:p
     (str (inc i) ". ")
     (render-item-fn item)]))

(defn- response [{:cooperation.response/keys [status content]}]
  [:span
   [:b (tr-enum status)]
   (when content
     (md/render-md-html content))])

(defn- opinion [{:cooperation.opinion/keys [status comment]}]
  [:span
   [:b (tr-enum status)]
   (when comment
     (md/render-md-html comment))])

(defn- applications-table [num applications]
  (let [rt (-> applications first :cooperation.application/response-type :db/ident)
        tr* #(tr [:cooperation :export rt %])
        third-parties (->>
                       applications
                       (group-by :cooperation.3rd-party/_applications)
                       vals
                       (map (fn [applications]
                              {:third-party-name (-> applications first
                                                     :cooperation.3rd-party/_applications
                                                     first
                                                     :cooperation.3rd-party/name)
                               :applications applications}))
                       (sort-by :third-party-name)
                       cu/indexed)]
    [:span
     [:h3 num ". " (tr* :header)]
     [:table {:border "1", :cellspacing "0", :cellpadding "0"}
      [:tbody
       [:tr
        [:td {:width "38"} (tr [:cooperation :export :seq#-column])]
        [:td {:width "169"}
         [:p
          [:strong (tr* :user-column)]]]
        [:td {:width "340"}
         [:p
          [:strong (tr* :content-column)]]]
        [:td {:width "385"}
         [:p
          [:strong (tr* :decision-column)]]]]

       (for [{tpi ::cu/i :keys [third-party-name applications]} third-parties
             :let [applications (->> applications
                                     (sort-by (comp :cooperation.response/date :cooperation.application/response))
                                     cu/indexed)]]
         [:tr
          [:td {:width "38" :valign "top"}
           [:p (str (inc tpi))]]
          [:td {:width "169" :valign "top"}
           [:p third-party-name]
           (numbered applications
                     #(some-> % :cooperation.application/response :cooperation.response/date date/format-date))]
          [:td {:width "340" :valign "top"}
           (numbered applications
                     #(some-> %
                              :cooperation.application/response
                              response))]
          [:td {:width "385" :valign "top"}
           (numbered applications
                     #(some-> %
                              :cooperation.application/opinion
                              opinion))]])]]]))

(def ^:private response-type-order
  [:cooperation.application.response-type/coordination
   :cooperation.application.response-type/opinion
   :cooperation.application.response-type/consent
   :cooperation.application.response-type/other])

(defn summary-table [db activity type]
  (let [applications (applications-by-response-type db activity type)
        project (d/pull db [:thk.project/name :thk.project/project-name]
                        (project-db/activity-project-id db activity))]
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
           [:h1 (or (:thk.project/project-name project) (:thk.project/name project))]
           [:h2 (tr-enum type)]
           (map-indexed
            (fn [i applications]
              (applications-table (inc i) applications))
            (remove nil?
                    (map applications response-type-order)))]]])))))

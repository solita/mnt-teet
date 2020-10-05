(ns teet.link.link-view
  "View for generic linking between entities"
  (:require [teet.ui.select :as select]
            [reagent.core :as r]
            [teet.link.link-controller :as link-controller]
            [teet.localization :as localization :refer [tr tr-enum]]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.util :refer [mapc]]))

(defmulti display :link/type)

(defmethod display :default
  [link]
  [:div "Unknown link type: " (pr-str link)])


(defmethod display :task
  [{:link/keys [info]}]
  (let [{:task/keys [type estimated-end-date assignee]} info]
    [:div {:class (<class common-styles/flex-row-space-between)}
     [:div (tr-enum type)]
     [:div (user-model/user-name assignee)]
     [:div (format/date estimated-end-date)]]))

(defn links
  "List links to other entities (like tasks).
  Shows input for adding new links."
  [{:keys [e! links from]}]
  (r/with-let [in-progress (r/atom false)
               add-link! (fn [to]
                           (e! (link-controller/->AddLink from (:db/id to) :task in-progress)))]
    [:div.links
     (mapc display links)
     (tr [:link :search])
     (when-not @in-progress
       [select/select-search
        {:e! e!
         :query (fn [text]
                  {:args {:lang @localization/selected-language
                          :text text
                          :from from
                          :types #{:task}}
                   :query :link/search})
         :on-change add-link!
         :format-result (fn [{:task/keys [type assignee estimated-end-date]}]
                          [:div {:class (<class common-styles/flex-row-space-between)}
                           [:div (tr-enum type)]
                           [:div (user-model/user-name assignee)]
                           [:div (format/date estimated-end-date)]])}])]))

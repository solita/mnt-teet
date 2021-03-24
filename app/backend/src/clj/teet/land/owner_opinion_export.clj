(ns teet.land.owner-opinion-export
  (:require [teet.localization :refer [tr tr-enum with-language]]
            [datomic.client.api :as d]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.util.html-export :as html-export-util]
            [teet.land.land-queries :as land-queries]
            [teet.project.project-model :as project-model]
            [teet.util.md :as md]))

(defn- opinions-by-type
  "Fetch all opinions for given activity with the given opinion type"
  [db activity type]
  (->>
    (d/q '[:find (pull ?opinion [*])
           :where
           [?opinion :land-owner-opinion/activity ?activity]
           [?opinion :land-owner-opinion/type ?type]
           [(missing? $ ?opinion :meta/deleted?)]
           :in $ ?activity ?type]
         db activity type)
    (db-api-large-text/with-large-text #{:land-owner-opinion/authority-position :land-owner-opinion/body})
    (map first)))

(def tr* #(tr [:land-owner-opinion :export %]))

(defn jrk-nr
  "String format for a running serial number of the estate and it's lands
  Only add number of land if the estate contains more than 1 unit"
  [estate-idx unit-idx unit-count]
  (if (= 1 unit-count)
    (str (inc estate-idx))
    (str (inc estate-idx) "." (inc unit-idx))))

(defn render-opinions
  [opinions]
  (let [amount (count opinions)]
    (if (= amount 1)
      (md/render-md-html (:land-owner-opinion/body (first opinions)))
      [:ol
       (for [opinion opinions]
         [:li
          (md/render-md-html (:land-owner-opinion/body opinion))])])))

(defn render-positions
  [opinions]
  (let [amount (count opinions)]
    (if (= amount 1)                                        ;; Don't use ordered list if only 1 opinion
      (md/render-md-html (:land-owner-opinion/authority-position
                           (first opinions)))
      [:ol
       (for [opinion opinions]
         [:li
          (if-let [position (:land-owner-opinion/authority-position
                              opinion)]
            (md/render-md-html position)
            [:p
             [:i (tr* :no-position-added)]])])])))

(defn unit-opinions-table
  [estates]
  [:div
   [:table {:border "1" :cellspacing "0" :cellpadding "5"}
    [:thead
     [:th (tr* :jrk)]
     [:th (tr* :estate-num-name)]
     [:th (tr* :cadastral-id-name)]
     [:th (tr* :opinion-body)]
     [:th (tr* :authority-position)]]
    [:tbody
     (doall
       (map-indexed
         (fn [estate-idx [estate-id units]]
           (let [unit-count (count units)]
             (map-indexed
               (fn [unit-idx {:keys [L_AADRESS TUNNUS teet-opinions] :as unit}]
                 [:tr
                  [:td {:style "vertical-align: top"}
                   [:p (jrk-nr estate-idx unit-idx unit-count)]]
                  [:td {:style "vertical-align: top"}
                   [:p estate-id]]
                  [:td {:style "vertical-align: top"}
                   [:p (str TUNNUS ";" L_AADRESS)]]
                  [:td {:style "vertical-align: top"}
                   (render-opinions teet-opinions)]
                  [:td {:style "vertical-align: top"}
                   (render-positions teet-opinions)]])
               units)))
         estates))]]])

(defn units-without-opinions-table
  [estates]
  [:div
   [:table {:border "1" :cellspacing "0" :cellpadding "5"}
    [:thead
     [:th (tr* :jrk)]
     [:th (tr* :estate-num-name)]
     [:th (tr* :cadastral-id-name)]]
    [:tbody
     (doall
       (map-indexed
         (fn [estate-idx [estate-id units]]
           (let [unit-count (count units)]
             (map-indexed
               (fn [unit-idx {:keys [L_AADRESS TUNNUS]}]
                 [:tr
                  [:td
                   [:p (jrk-nr estate-idx unit-idx unit-count)]]
                  [:td
                   [:p estate-id]]
                  [:td
                   [:p (str TUNNUS ";" L_AADRESS)]]])
               units)))
         estates))]]])

(defn owner-opinion-summary-table [db activity opinion-type {:keys [api-url api-secret] :as _config}]
  (let [opinions (opinions-by-type db activity opinion-type)
        project (ffirst
                  (d/q '[:find (pull ?project [:thk.project/name :thk.project/project-name
                                               :db/id :thk.project/id])
                         :in $ ?activity
                         :where
                         [?lifecycle :thk.lifecycle/activities ?activity]
                         [?project :thk.project/lifecycles ?lifecycle]]
                       db activity))
        units (land-queries/project-cadastral-units db api-url api-secret (:thk.project/id project))
        opinion-unit-ids (set (map :land-owner-opinion/land-unit opinions))

        units-with-opinions (->> units
                                 (filter #(opinion-unit-ids (:teet-id %)))
                                 (mapv (fn [{:keys [teet-id] :as unit}]
                                         (assoc unit
                                           :teet-opinions
                                           (filterv #(= teet-id (:land-owner-opinion/land-unit %))
                                                    opinions))))
                                 (group-by :KINNISTU)
                                 (sort-by (fn [[estate _]]
                                            estate)))
        units-without-opinions (->> units
                                    (filter #(not (opinion-unit-ids (:teet-id %))))
                                    (group-by :KINNISTU)
                                    (sort-by (fn [[estate _]]
                                               estate)))]
    (with-language :et
      (html-export-util/html-export-helper
        {:title (tr [:land-owner-opinion :export :title] {:opinion-type (tr-enum opinion-type)})
         :content [:div#export
                   [:h2 (project-model/get-column project :thk.project/project-name)]
                   [:h1 (tr-enum opinion-type)]
                   [:h3 (tr* :units-with-opinions)]
                   (unit-opinions-table units-with-opinions)
                   [:h3 (tr* :units-without-opinions)]
                   (units-without-opinions-table units-without-opinions)]}))))

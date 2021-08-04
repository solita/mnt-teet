(ns teet.contract.contract-responsibilities-view
  (:require [clojure.string :as str]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.typography :as typography]
            teet.contract.contract-spec
            [teet.user.user-model :as user-model]
            [teet.ui.common :as ui-common]
            [teet.ui.table :as table]
            [teet.ui.url :as url]
            [teet.ui.util :as ui-util]
            [teet.util.collection :as cu]))

(defn simple-table-with-many-bodies
  [table-headings groups]
  [:table {:style {:border-collapse :collapse
                   :width "100%"}}
   [:colgroup
    (for [[_ opts] table-headings]
      [:col {:span "1"
             :style (merge {}
                           (select-keys opts [:width]))}])]
   [:thead
    [:tr {:class (<class contract-style/responsibilities-table-row-style)}
     (ui-util/mapc
       (fn [[heading opts]]
         [:td (merge
                {:class (<class contract-style/responsibilities-table-heading-cell-style)}
                (dissoc opts :width))
          heading])
       table-headings)]]
   (for [[body-title body-rows] groups]
     [:tbody
      (when body-title
        [:tr
         [:th {:colSpan 4
               :class (<class contract-style/responsibilities-table-header-style)}
          body-title]])
      (ui-util/mapc
       (fn [row]
         [:tr {:class (<class contract-style/responsibilities-table-row-style)}
          (ui-util/mapc
           (fn [[column {:keys [style class] :as opts}]]
             [:td (merge
                   {:style (merge {} style)
                    :class (herb/join
                            (<class contract-style/responsibilities-table-cell-style)
                            (when class
                              class))}
                   (dissoc opts :style :class))
              column])
           row)])
       body-rows)])])

(defn- representative-info->company-name [representative-info]
  (-> representative-info :company-contract :company-contract/company :company/name))
(defn- representative-info->roles [representative-info]
  (or (some->> representative-info :employee :company-contract-employee/role (map tr-enum) (str/join ", "))
      "..."))
(defn- representative-info->name [representative-info]
  (or (some-> representative-info :employee :company-contract-employee/user user-model/user-name)
      "..."))
(defn- representative-info->lead-partner? [representative-info]
  (-> representative-info :company-contract :company-contract/lead-partner?))

(defn- partner-representatives-table [partner-representatives]
  [simple-table-with-many-bodies
   [[(tr [:contract :representatives :header :company]) {:align :left}]
    [(tr [:contract :representatives :header :responsible]) {:align :left}]
    [(tr [:contract :representatives :header :role]) {:align :left}]]
   [[nil ;; no group level heading
     (into []
           (for [representative-info
                 ;; Lead partner representatives first, the rest of the companies in alphabetical order
                 (sort-by (juxt (complement representative-info->lead-partner?)
                                representative-info->company-name
                                representative-info->name)
                          partner-representatives)]
             [[[:<>
                [:span {:class (<class common-styles/margin-right 0.5)}
                 (representative-info->company-name representative-info)]
                (when (representative-info->lead-partner? representative-info)
                  [ui-common/primary-tag (tr [:contract :lead-partner])])]
               {:align :left}]
              [(representative-info->name representative-info) {:align :left}]
              [(representative-info->roles representative-info) {:align :left}]]))]]])

(defn targets-responsibilities
  [targets]
  [:div
   (for [target targets
         :let [tasks (get-in target [:activity :activity/tasks])
               groups (group-by :task/group tasks)]]
     [:div {:style {:padding "2rem 0 2rem 0"}}
      [:div {:class (herb/join (<class common-styles/flex-row-w100-space-between-center)
                               (<class common-styles/margin-bottom 2))}
       [typography/Heading1 (get-in target [:project :thk.project/name])]
       [typography/Heading4 (if (get-in target [:target :activity/name])
                              (tr [:enum (get-in target [:target :activity/name])])
                              "")]
       [url/Link
        (merge (:target-navigation-info target)
               {:component-opts {:data-cy "target-responsibility-activity-link"}})
        (tr [:link :target-view-activity])]]
      (when (not-empty groups)
        (let [bodies (->> groups
                          (mapv (fn [[group items]]
                                  [(tr-enum group)
                                   (->> items
                                        (mapv (fn [item]
                                                [[[url/Link (:navigation-info item)
                                                   (tr-enum (:task/type item))]]
                                                 [(:owner item)]
                                                 [(if (nil? (:task/assignee item))
                                                    ""
                                                    (user-model/user-name (:task/assignee item)))]
                                                 [(tr-enum (:task/status item))]]))
                                        sort)]))
                          (sort-by first))]
          [simple-table-with-many-bodies [[(tr [:contract :responsible :header :task]) {:width "35%"}]
                                          [(tr [:contract :responsible :header :tram-reviewer]) {:width "25%"}]
                                          [(tr [:contract :responsible :header :company-responsible]) {:width "25%"}]
                                          [(tr [:contract :responsible :header :status]) {:width "15%"}]]
           bodies]))])])

(defn responsibilities-page
  [e! app contract]
  (let [targets (:thk.contract/targets contract)
        partner-representatives (:partner-representatives contract)]
    [:div {:class (<class contract-style/contract-responsibilities-container-style)}
     [contract-common/contract-heading e! app contract]
     [:div {:class (<class contract-style/responsibilities-page-container)}
      (when (not-empty partner-representatives)
        [:div {:class (<class common-styles/margin-top 2)}
         [typography/Heading1 {:class (<class common-styles/margin-top 2)}
          (tr [:contract :representatives :heading])]
         [partner-representatives-table partner-representatives]])
      (when (not-empty targets)
        [:div {:class (<class common-styles/margin-top 2)}
         [typography/Heading1 {:class (<class common-styles/margin-top 2)}
          (tr [:contract :table-heading :task-responsibilities])]
         [targets-responsibilities targets]])]]))

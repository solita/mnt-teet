(ns teet.admin.admin-view
  "Admin user interface"
  (:require [teet.admin.admin-controller :as admin-controller]
            [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell Paper Collapse]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [herb.core :refer [<class]]
            [clojure.string :as str]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            teet.user.user-spec
            [teet.ui.select :as select]
            [teet.ui.util :refer [mapc]]
            [teet.ui.query :as query]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]
            [teet.ui.common :as common-ui]
            [teet.ui.context :as context]
            [teet.user.user-model :as user-model]
            [teet.util.date :as date]))

(defn user-save-form-button
  [invalid-attributes {:keys [validate]}]
  [:div
   [buttons/button-primary
    {:on-click validate
     :type :submit
     :disabled (boolean (seq invalid-attributes))}
    [:span (tr [:buttons :save])]]])

(defn user-save-form-button*
  []
  (context/consume
    :form
    (fn [{:keys [invalid-attributes value footer]}]
      [user-save-form-button @invalid-attributes value (update footer :validate
                                (fn [validate]
                                  (when validate
                                    #(validate value))))])))

(defn user-form-cancel-button*
  []
  (context/consume
    :form
    (fn [{:keys [footer]}]
      [buttons/button-secondary {:on-click (:cancel footer)}
       (tr [:buttons :cancel])])))

(defn user-form-fields
  []
  [:div {:style {:background-color theme-colors/gray-lightest
                 :padding "1rem"}}
   [:div {:style {:display :flex
                  :flex-direction :row}}
    [:div {:style {:flex 1
                   :padding "0 1rem"}}
     [typography/Heading3 {:style {:margin-bottom "1rem"}}
      "Mandatory information"]
     [:div {:style {:margin-bottom "1rem"}}
      [form/field :user/person-id
       [TextField {}]]]
     [:div {:style {:margin-bottom "1rem"}}
      [form/field :user/email
       [TextField {}]]]]
    [:div {:style {:flex 1
                   :padding "0 1rem"}}
     [typography/Heading3 {:style {:margin-bottom "1rem"}}
      "Optional information"]

     [:div {:style {:margin-bottom "1rem"}}
      [form/field :user/global-role
       [select/form-select {:format-item #(if % (name %) (str "- " (tr [:admin :user-no-global-role]) " -"))
                            :items [nil
                                    :admin :manager
                                    :internal-consultant
                                    :external-consultant]}]]]

     [:div {:style {:margin-bottom "1rem"}}
      [form/field :user/company
       [TextField {}]]]
     [:div {:style {:margin-bottom "1rem"}}
      [form/field :user/phone-number
       [TextField {}]]]]]

   [user-form-cancel-button*]])

(defn- create-user-form [e! {:keys [form-value on-change-event save-event cancel-event]}]
  [:div {:class (<class common-styles/margin-bottom 1)}
   [form/form2 {:e! e!
                :on-change-event on-change-event
                :save-event save-event
                :cancel-event cancel-event
                :value form-value
                :spec :admin/create-user}
    [common-ui/hierarchical-container {:heading-content [:div {:class (<class common-styles/flex-row-space-between)
                                                               :style {:padding "1rem"}}
                                                         [typography/Heading3 {:style {:margin-bottom 0}} (tr [:admin :create-user])]
                                                         [user-save-form-button*]]
                                       :children [[user-form-fields]]}]]])

(defn- user-permissions [permissions]
  [:ul
   (doall
    (for [{:permission/keys [role valid-from valid-until projects]} (sort-by (juxt :permission/role
                                                                                   :permission/valid-from)
                                                                             permissions)]
      [:li (str (name role)
                (when-not projects " (global)")
                ": "
                (format/date valid-from) "-" (format/date valid-until))
       (when projects
         [:ul
          (doall (for [{:thk.project/keys [id name]} (sort-by :thk.project/id projects)]
                   [:li (str id " " name)]))])]))])

(defn user-row-heading
  [{:user/keys [company email phone-number global-role] :as user} open? toggle-open]
  (let [contact-info (str/join " / "
                               (filterv
                                 not-empty
                                 [company email phone-number]))]
    [:div {:class (<class common-styles/flex-row-space-between)
           :style {:padding "1rem"}}
     [:div {:style {:flex 1
                    :margin-right "1rem"}}
      [typography/Heading3 {:style {:margin-bottom 0}}
       (or (user-model/user-name user)
           (tr [:admin :user-has-no-name]))]
      [typography/SmallText
       (:user/person-id user)]]
     [:div {:style {:flex 1}}
      [typography/GreyText
       (tr [:admin :user-global-role])]
      [typography/SmallText
       (if global-role
         (name global-role)
         (tr [:admin :user-no-global-role]))]]
     [:div {:style {:flex 3}}
      [typography/GreyText
       (tr [:admin :user-contact-information])]
      [typography/SmallText
       (if (seq contact-info)
         contact-info
         (tr [:admin :no-user-contact-information]))]]
     [:div {:style {:flex 1
                    :display :flex
                    :justify-content :flex-end}}
      [buttons/button-secondary
       {:on-click toggle-open}
       (if open?
         (tr [:buttons :close])
         (tr [:buttons :edit]))]]]))

(defn edit-user-form
  [e! user]
  (r/with-let [form-value (r/atom (select-keys
                                    user
                                    [:user/email :user/company :user/phone-number :user/person-id :user/global-role]))]
    [form/form2
     {:e! e!
      :value @form-value
      :spec :admin/edit-user
      :on-change-event (form/update-atom-event form-value merge)
      :save-event #(admin-controller/->EditUser @form-value)}
     [:div {:style {:background-color theme-colors/gray-lightest
                    :padding "1rem"}}
      [typography/GreyText {:style {:margin-bottom "1rem"}}
       (tr [:admin :role-and-contact-information])]
      [:div {:style {:display :flex
                     :flex-direction :row}}
       [:div {:style {:flex 1
                      :padding "0 1rem"}}
        [:div {:style {:margin-bottom "1rem"}}
         [form/field :user/global-role
          [select/form-select {:format-item #(if % (name %) (str "- " (tr [:admin :user-no-global-role]) " -"))
                               :items [nil
                                       :admin :manager
                                       :internal-consultant
                                       :external-consultant]}]]]
        [:div {:style {:margin-bottom "1rem"}}
         [form/field :user/email
          [TextField {}]]]]
       [:div {:style {:flex 1
                      :padding "0 1rem"}}
        [:div {:style {:margin-bottom "1rem"}}
         [form/field :user/company
          [TextField {}]]]
        [:div {:style {:margin-bottom "1rem"}}
         [form/field :user/phone-number
          [TextField {}]]]]]
      [form/footer2]]]))

(defn permission-row
  [{:permission/keys [projects valid-from valid-until role]}]
  (let [project (first projects)]                           ;; There can only be one project per permission
    [:div {:style {:padding "1rem"
                   :border-top (str "1px solid " theme-colors/gray-lighter)}}
     [:div
      [typography/Heading3
       (if project
         (str
           (:thk.project/id project) " "
           (or (:thk.project/project-name project) (:thk.project/name project))
           " – " (name role))
         (str (tr [:admin :user-global-role]) " – " (name role)))]
      [typography/SmallGrayText (when valid-from
                                  (str (format/date valid-from) " – " (format/date valid-until)))]]]))

(defn user-permissions-and-history
  [{:user/keys [permissions] :as user}]
  (r/with-let [show-history? (r/atom false)
               toggle-show-history #(swap! show-history? not)]
    (let [current-perms (->> permissions
                             (sort-by (juxt :permission/valid-until :permission/valid-from))
                             reverse
                             (filter #(or (nil? (:permission/valid-until %))
                                          (date/in-future? (:permission/valid-until %)))))
          past-perms (->> permissions
                          (sort-by (juxt :permission/valid-until :permission/valid-from))
                          reverse
                          (filter #(and (some? (:permission/valid-until %))
                                        (date/in-past? (:permission/valid-until %)))))]
      [:div {:class (<class common-styles/margin-bottom 1.5)}
       [:div
        [typography/GreyText (tr [:admin :current-permissions])]
        (if (seq current-perms)
          (mapc
            permission-row
            current-perms)
          [:p {:style {:margin "0.5rem 0"}}
           (tr [:admin :no-current-permissions])])]
       (when @show-history?
         [:div
          [typography/GreyText (tr [:admin :past-permissions])]
          (mapc
            permission-row
            past-perms)])
       (when (seq past-perms)
         [:div {:style {:display :flex
                        :justify-content :center}}
          [buttons/link-button {:on-click toggle-show-history}
           (if @show-history?
             (tr [:admin :hide-history])
             (tr [:admin :show-history]))]])])))


(defn user-state-controls
  [e! {:user/keys [deactivated?] :as user}]
  (if deactivated?
    [buttons/button-primary
     {:on-click #(e! (admin-controller/->ReactivateUser user))}
     (tr [:admin :reactivate-user-btn])]

    [buttons/delete-button-with-confirm
     {:action #(e! (admin-controller/->DeactivateUser user))
      :modal-title (tr [:admin :deactivate-user-modal-title])
      :modal-text (tr [:admin :deactivate-user-modal-text] {:name-to-deactivate (user-model/user-name user)})
      :confirm-button-text (tr [:admin :deactivate])}
     (tr [:admin :deactivate-user-btn])]))

(defn user-row-content
  [e! open? user toggle-open]
  []
  [Collapse {:in open?
             :mount-on-enter true}
   [:div {:style {:background-color theme-colors/gray-lightest
                  :padding "1rem"}}
    [edit-user-form e! user]
    [user-permissions-and-history user]
    [user-state-controls e! user]
    ]])

(defn user-row
  [e! user]
  (r/with-let [open? (r/atom false)
               toggle-open #(swap! open? not)]
    (let [global-role (some
                        (fn [permission]
                          (when (and (nil? (:permission/projects permission))
                                     (nil? (:permission/valid-until permission)))
                            permission))
                        (:user/permissions user))
          user (assoc user :user/global-role (:permission/role global-role))]
      [:div {:class (<class common-styles/margin-bottom 0.5)}
       [common-ui/hierarchical-container
        {:heading-content [user-row-heading user @open? toggle-open]
         :show-polygon? @open?
         :children [^{:key (str (:db/id user) "-row-content")}
                    [user-row-content e! @open? user toggle-open]]}]])))


(defn user-list
  [e! admin users]
  [:div
   (doall
     (for [{:user/keys [id] :as user}
           (sort-by (juxt :user/family-name
                          :user/given-name) users)]
       ^{:key (str "row-" id)}
       [user-row e! user]))])

(def searchable-user-groups
  {nil [:admin :all-users]
   :manager [:admin :managers]
   :admin [:admin :admins]
   :internal-consultant [:admin :internal-consultants]
   :external-consultant [:admin :external-consultants]
   :deactivated [:admin :deactivated]})

(defn search-shortcuts
  [value on-change]
  [:div {:style {:margin-bottom "1rem"}}
   [typography/SmallGrayText (tr [:admin :search-shortcuts])]
   [:div {:class (<class common-styles/flex-column-start)}
    (doall
      (for [[term translation-path] searchable-user-groups]
        ^{:key (str translation-path)}
        [:<>
         [buttons/link-button {:on-click #(on-change term)
                               :class (<class common-styles/white-link-style (= value term))}
          (tr translation-path)]]))]])

(def user-filters
  [:user/given-name
   :user/family-name
   :user/person-id
   :project])

(defn search-inputs
  [e! values on-change]
  [:div
   (doall
     (for [field user-filters]
       ^{:key field}
       [TextField {:value (field values)
                   :start-icon icons/action-search
                   :style {:margin-bottom "1rem"
                           :display :block}
                   :label (tr [:fields field])
                   :on-change #(on-change field (-> % .-target .-value))}]))])

(defn user-search-filters
  [e! filtering-atom]
  (r/with-let [change-group #(swap! filtering-atom assoc :user-group %)
               input-change (fn [key value]
                              (swap! filtering-atom assoc key value))]
    [:div {:style {:min-width "300px"
                   :background-color theme-colors/gray-dark
                   :color theme-colors/white}}
     [:div {:style {:padding "1rem"}}
      [search-shortcuts (:user-group @filtering-atom) change-group]
      [search-inputs e! @filtering-atom input-change]]]))

(defn admin-page [e! {admin :admin
                      route :route}]
  (r/with-let [filtering-atom (r/atom {:user-group nil})]
    (let [user-form (:create-user admin)]
      [:div {:style {:padding "1.875rem 1.5rem"
                     :display :flex
                     :height "calc(100vh - 220px)"
                     :flex-direction :column
                     :flex 1}}
       [Paper {:style {:display :flex
                       :flex 1}}
        [user-search-filters e! filtering-atom]
        [:div {:style {:flex 1
                       :overflow-y :scroll
                       :max-height "calc(100vh - 170px)"
                       :padding "1rem"}}
         [:div {:class (<class common-styles/flex-row-space-between)}
          [typography/Heading1 "Teet Users"]
          (when (not user-form)
            [buttons/button-primary
             {:on-click (e! admin-controller/->CreateUser)}
             (tr [:admin :create-user])])]
         (when user-form
           [create-user-form e! {:form-value user-form
                                 :on-change-event admin-controller/->UpdateUserForm
                                 :save-event admin-controller/->SaveUser
                                 :cancel-event admin-controller/->CancelUser}])

         [query/debounce-query
          {:e! e!
           :query :admin/list-users
           :args {:payload @filtering-atom
                  :refresh (:admin-refresh route)}
           :simple-view [user-list e! admin]}
          500]]]])))

(defn- inspector-value [link? value]
  (if link?
    [:<>
     [:a {:href (str "#/admin/inspect/" (:db/id value))}
      (str (:db/id value))]
     (str " " (str/join ", "
                        (for [[k v] (sort-by first value)
                              :when (not= k :db/id)]
                          v)))]
    (str value)))

(defn inspector-page [_e! _app {:keys [entity ref-attrs linked-from]}]
  [:div
   [typography/Heading2 "Attribute values for entity"]
   [Table {}
    [TableHead {}
     [TableRow
      [TableCell {} "Attribute"]
      [TableCell {} "Value"]]]
    [TableBody {}
     (doall
      (for [[k v] (sort-by first (seq entity))
            :let [link? (ref-attrs k)]]
        ^{:key (str k)}
        [TableRow {}
         [TableCell {} (str k)]
         [TableCell {}
          (if (vector? v)
            [:ul
             (doall
              (map-indexed
               (fn [i v]
                 ^{:key (str i)}
                 [:li [inspector-value link? v]]) v))]
            [inspector-value link? v])]]))]]

   (when (seq linked-from)
     [:<>
      [typography/Heading2 "Links from other entities"]
      [Table {}
       [TableHead {}
        [TableRow
         [TableCell {} "Attribute"]
         [TableCell {} "Entities"]]]
       [TableBody {}
        (doall
         (for [[k v] (sort-by first (seq linked-from))]
           ^{:key (str k)}
           [TableRow {}
            [TableCell {} (str k)]
            [TableCell {}
             [:ul
              (doall
               (map-indexed
                (fn [i v]
                  ^{:key (str i)}
                  [:li [inspector-value true {:db/id v}]]) v))]]]))]]])])

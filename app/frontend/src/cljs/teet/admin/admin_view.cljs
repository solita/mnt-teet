(ns teet.admin.admin-view
  "Admin user interface"
  (:require [teet.admin.admin-controller :as admin-controller]
            [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [clojure.string :as str]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            teet.user.user-spec
            [teet.ui.select :as select]))

(defn- create-user-form [e! form]
  [:div
   [typography/Heading3 (tr [:admin :create-user])]
   [form/form {:e! e!
               :on-change-event admin-controller/->UpdateUserForm
               :save-event admin-controller/->SaveUser
               :cancel-event admin-controller/->CancelUser
               :value form
               :spec :admin/create-user}
    ^{:attribute :user/person-id}
    [TextField {:variant "outlined"}]

    ^{:attribute :user/add-global-permission}
    [select/form-select {:format-item #(if % (name %) "- no global permission -")
                         :items [nil
                                 :admin :manager
                                 :internal-consultant
                                 :external-consultant]}]]])

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

(defn admin-page [e! {admin :admin} users]
  [:div
   [Table {}
    [TableHead {}
     [TableRow
      [TableCell {} (tr [:fields :user/person-id])]
      [TableCell {} (tr [:fields :user/given-name])]
      [TableCell {} (tr [:fields :user/family-name])]
      [TableCell {} (tr [:fields :user/roles])]]]
    [TableBody {}
     (doall
      (for [{:user/keys [person-id given-name family-name permissions id]} (sort-by (juxt :user/family-name
                                                                                          :user/given-name)
                                                                                    users)]
        ^{:key id}
        [TableRow {}
         [TableCell {} person-id]
         [TableCell {} given-name]
         [TableCell {} family-name]
         [TableCell {}
          [user-permissions permissions]]]))]]
   (if-let [form (:create-user admin)]
     [create-user-form e! form]
     [buttons/button-primary {:on-click (e! admin-controller/->CreateUser)}
      (tr [:admin :create-user])])])

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

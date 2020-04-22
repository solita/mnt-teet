(ns teet.admin.admin-view
  "Admin user interface"
  (:require [teet.admin.admin-controller :as admin-controller]
            [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [clojure.string :as str]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
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
      (for [{:user/keys [person-id given-name family-name roles id]} users]
        ^{:key id}
        [TableRow {}
         [TableCell {} person-id]
         [TableCell {} given-name]
         [TableCell {} family-name]
         [TableCell {} (str/join ", " (map name roles))]]))]]
   (if-let [form (:create-user admin)]
     [create-user-form e! form]
     [buttons/button-primary {:on-click (e! admin-controller/->CreateUser)}
      (tr [:admin :create-user])])])

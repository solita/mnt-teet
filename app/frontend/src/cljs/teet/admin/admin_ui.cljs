(ns teet.admin.admin-ui
  "Common admin UI components"
  (:require [teet.ui.common :as common-ui]
            [teet.common.common-styles :as common-styles]
            [teet.ui.icons :as icons]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]))

(defn admin-context-menu
  "Displays the top context menu for all admin pages"
  []
  [:div {:class (<class common-styles/top-info-spacing)}
   [common-ui/context-menu {:label (tr [:admin :admin-menu])
                            :icon [icons/navigation-menu]
                            :items [{:label (tr [:admin :title-users])
                                     :link {:href "../#/admin/users"}
                                     :icon [icons/action-verified-user]}
                                    {:label (tr [:admin :title-indexes])
                                     :link {:href "../#/admin/indexes"}
                                     :icon [icons/action-list]}]}]])
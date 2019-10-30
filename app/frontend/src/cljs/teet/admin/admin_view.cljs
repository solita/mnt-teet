(ns teet.admin.admin-view
  "Admin user interface"
  (:require [teet.admin.admin-controller :as admin-controller]
            [teet.user.user-controller :as user-controller]))

(defn admin-page [e! app users]
  [:div "this is the admin page!"])

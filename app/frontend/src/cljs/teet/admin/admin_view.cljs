(ns teet.admin.admin-view
  "Admin user interface"
  (:require [teet.admin.admin-controller :as admin-controller]
            [teet.user.user-controller :as user-controller]
            [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell]]
            [clojure.string :as str]))

(defn admin-page [e! app users]
  [:div "this is the admin page!"
   [Table {}
    [TableHead {}
     [TableRow
      [TableCell {} "person id"]
      [TableCell {} "given name"]
      [TableCell {} "family name"]
      [TableCell {} "roles"]]]
    [TableBody {}
     (doall
      (for [{:user/keys [person-id given-name family-name roles id]} users]
        ^{:key id}
        [TableRow {}
         [TableCell {} person-id]
         [TableCell {} given-name]
         [TableCell {} family-name]
         [TableCell {} (str/join ", " (map name roles))]]))]]])

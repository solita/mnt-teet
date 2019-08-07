(ns teet.routes
  (:require [compojure.core :refer [GET POST routes]]
            [compojure.route :refer [resources]]
            [teet.index.index-page :as index-page]))

(defn teet-routes []
  (routes
   (GET "/" _
        (index-page/index-route))

   (resources "/")))

(ns teet.routes
  (:require [compojure.core :refer [GET POST routes]]
            [compojure.route :refer [resources]]
            [teet.index.index-page :as index-page]
            [cheshire.core :as cheshire]))

(defn teet-routes []
  (routes
   (GET "/" _
        (index-page/index-route))

   (POST "/userinfo" req
         {:status 200
          :headers {"Content-Type" "application/json"}
          :body (cheshire/encode (get-in req [:session :user]))})

   (resources "/")))

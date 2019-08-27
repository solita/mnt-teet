(ns teet.routes
  (:require [compojure.core :refer [GET POST routes]]
            [compojure.route :refer [resources files]]
            [teet.index.index-page :as index-page]
            [cheshire.core :as cheshire]
            [teet.login.login-api-token :as login-api-token]))

(defn teet-routes [config]
  (routes
   (GET "/" _
        (index-page/index-route config))

   (POST "/userinfo" req
         {:status 200
          :headers {"Content-Type" "application/json"}
          :body (cheshire/encode
                 (if-let [user (get-in req [:session :user])]
                   (merge user
                          {:authenticated? true
                           :api-token (login-api-token/create-token
                                       (get-in config [:api :shared-secret])
                                       (get-in config [:api :role])
                                       user)})
                   {:authenticated? false}))})

   (if (= :dev (:mode config))
     ;; In dev mode, serve files under frontend figwheel build
     (routes
      (POST "/success" req
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (cheshire/encode {:success "great"})})
      (POST "/fail" req
            {:status 500
             :headers {"Content-Type" "application/json"}
             :body (cheshire/encode {:success "not"})})
      (files "/" {:root "../frontend/target/public"})
      (files "/" {:root "../frontend/resources/public"}))

     ;; In prod mode, serve packaged resources
     (resources "/"))))

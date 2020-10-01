(ns teet.routes
  (:require [compojure.core :refer [GET POST routes]]
            [compojure.route :refer [resources files]]
            [teet.index.index-page :as index-page]
            [cheshire.core :as cheshire]
            [teet.auth.jwt-token :as jwt-token]))

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
                           :api-token (jwt-token/create-token
                                       (get-in config [:api :shared-secret])
                                       (get-in config [:api :role])
                                       user)})
                   {:authenticated? false}))})

   (if (= :dev (:mode config))
     ;; In dev mode, serve files under frontend figwheel build
     (routes
      (POST "/success" _req
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body (cheshire/encode {:success "great"})})
      (POST "/fail" _req
            {:status 500
             :headers {"Content-Type" "application/json"}
             :body (cheshire/encode {:success "not"})})
      ;; Simulates the JSON file written by the deploy script
      (GET "/js/deploy.json" _req
           {:status 200
            :headers {"Content-Type" "application/json"}
            :body (cheshire/encode {:commit "the-commit-sha"
                                    :status "deployed"
                                    :timestamp "Mon Feb 10 13:51:35 UTC 2020"})})
      (files "/" {:root "../frontend"})
      (files "/" {:root "../frontend/target/public"})
      (files "/" {:root "../frontend/resources/public"})
      (files "/" {:root "../common/resources/public"}))

     ;; In prod mode, serve packaged resources
     (resources "/"))))

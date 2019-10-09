(ns teet.login.login-queries
  (:require [teet.db-api.core :as db-api]
            [teet.environment]
            [datomic.client.api :as d]
            [taoensso.timbre :as log]))


(defn user-ok? [user site-password]
  ;; mock impl, replace with authentication
  (and (some? (:user/id user))
       (teet.environment/check-site-password site-password)))

(defmethod db-api/query :user-session [{db :db} params]
  (let [user (:user params)
        site-password (:site-password params)]
    (log/info "backend :user-session query received params:" params)
    (if (user-ok? user site-password)
      ^{:format :raw}
      {:status 200
       :cookies {;; mock impl, replace with signed version that includes freshness info
                 "site-password" site-password
                 "user-uuid" {:value (:user/id user)
                              ;; :secure true ;; FIXME dev setup is not https, detect dev mode here
                              :http-only true
                              :same-site :strict
                              :max-age 600 ;; increase after testing
                              }}

       "Content-Type" "application/json"
       :body "{\"ok\": true}"}
      ;; else
      ^{:format :raw}
      {:status 403
       :cookies {
                 "site-password" ""
                 "user-uuid" ""}

       "Content-Type" "application/json"
       :body "{\"ok\": false}"}
      )))

(defmethod db-api/query-authorization :user-session [_ _]
  true)

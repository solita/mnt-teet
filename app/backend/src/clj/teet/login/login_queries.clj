(ns teet.login.login-queries
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [taoensso.timbre :as log]))


(defn user-ok? [user]
  ;; mock impl, replace with authentication
  (some? (:user/id user)))

(defmethod db-api/query :user-session [{db :db} {user :user}]
  (log/info "backend :user-session query received user:" user)
  (when (user-ok? user)
    ^{:format :raw}
    {:status 200
     :cookies {;; mock impl, replace with signed version that includes freshness info
               "user-uuid" {:value (:user/id user)
                            ;; :secure true ;; FIXME dev setup is not https, detect dev mode here
                            :http-only true
                            :same-site :strict
                            :max-age 600 ;; increase after testing
                            }}

     "Content-Type" "application/json"
     :body "{\"ok\": true}"}))

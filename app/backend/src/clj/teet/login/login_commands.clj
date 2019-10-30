(ns teet.login.login-commands
  (:require [teet.db-api.core :as db-api]
            [teet.environment :as environment]
            [taoensso.timbre :as log]
            [teet.login.login-api-token :as login-api-token]
            [datomic.client.api :as d]))


(defn user-roles [conn id]
  ;; FIXME: implement
  #{:user})

(defmethod db-api/command! :login [{conn :conn}
                                   {:user/keys [id given-name family-name email person-id]
                                    site-password :site-password}]
  (d/transact conn {:tx-data [{:user/id id}]})
  #_(when (not= :dev (environment/config-value :env))
    (log/warn "Demo login can only be used in :dev environment")
    (throw (ex-info "Demo login not allowed"
                    {:demo-user user})))

  (if (environment/check-site-password site-password)
    (let [secret (environment/config-value :auth :jwt-secret)]
      {:token (login-api-token/create-token secret "teet_user"
                                            {:given-name given-name
                                             :family-name family-name
                                             :person-id person-id
                                             :email email
                                             :id id})
       :roles (user-roles conn id)})
    {:error :incorrect-site-password}))

(defmethod db-api/command-authorization :login [_ _]
  ;; Always allow login to be used
  nil)

(defmethod db-api/command! :refresh-token [{conn :conn
                                            {:user/keys [id given-name family-name email person-id]} :user} _]
  {:token (login-api-token/create-token (environment/config-value :auth :jwt-secret) "teet_user"
                                        {:given-name given-name
                                         :family-name family-name
                                         :person-id person-id
                                         :email email
                                         :id id})
   :roles (user-roles conn id)})

(defmethod db-api/command-authorization :refresh-token [{user :user} _]
  (when-not (and user
                 (every? #(contains? user %)
                         [:user/id :user/given-name :user/family-name :user/person-id]))
    (throw (ex-info "Can't refresh token, user information missing"
                   {:user user}))))

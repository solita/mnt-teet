(ns teet.login.login-commands
  (:require [teet.db-api.core :as db-api]
            [teet.environment :as environment]
            [teet.login.login-api-token :as login-api-token]
            [datomic.client.api :as d]
            [teet.log :as log]))


(defn user-roles
  "Given a datomic connection and a user uuid, return a set of user's roles."
  [conn id]
  (-> conn
      d/db
      (d/pull '[:user/roles] [:user/id id])
      :user/roles
      set))

(defn ensure-user!
  "Make sure that the given user exists in the database.
  If user does not exist, creates it.

  Returns user id (uuid)."

  [conn person-id given-name family-name]
  (log/info "Ensure user: " person-id given-name family-name)
  (if-let [id (-> conn d/db
                  (d/pull '[:user/id] [:user/person-id person-id])
                  :user/id)]
    id
    (let [new-id (java.util.UUID/randomUUID)]
      (d/transact conn {:tx-data [{:user/id new-id
                                   :user/person-id person-id
                                   :user/given-name given-name
                                   :user/family-name family-name}]})
      new-id)))

(defmethod db-api/command! :login [{conn :conn}
                                   {:user/keys [id given-name family-name email person-id]
                                    site-password :site-password}]
  (d/transact conn {:tx-data [{:user/id id}]})
  #_(when (not= :dev (environment/config-value :env))
    (log/warn "Demo login can only be used in :dev environment")
    (throw (ex-info "Demo login not allowed"
                    {:demo-user user})))

  (if (environment/check-site-password site-password)
    (let [secret (environment/config-value :auth :jwt-secret)
          roles (user-roles conn id)]
      {:token (login-api-token/create-token secret "teet_user"
                                            {:given-name given-name
                                             :family-name family-name
                                             :person-id person-id
                                             :email email
                                             :id id
                                             :roles roles})
       :roles roles})
    {:error :incorrect-site-password}))

(defmethod db-api/command-authorization :login [_ _]
  ;; Always allow login to be used
  nil)


(defn on-tara-login [claims]
  (let [conn (environment/datomic-connection)
        secret (environment/config-value :auth :jwt-secret)

        ;; Destructure person info from claims
        {person-id "sub"
         {:strs [given_name family_name]} "profile_attributes"} claims

        id (ensure-user! conn person-id given_name family_name)
        roles (user-roles conn id)
        response {:status 302
                  :headers {"Location"
                            (str (environment/config-value :base-url)
                                 "#/login?token="
                                 (login-api-token/create-token
                                  secret "teet_user"
                                  {:given-name given_name
                                   :family-name family_name
                                   :person-id person-id
                                   :id id
                                   :roles roles}))}
                  :body "Redirecting to TEET"}]
    (log/info "on-tara-login response: " response)
    response))

(defmethod db-api/command! :login/check-session-token [{session :session} _]
  (:jwt-token session))

(defmethod db-api/command-authorization :login/check-session-token [_ _]
  nil)

(defmethod db-api/command! :refresh-token [{conn :conn
                                            {:user/keys [id given-name family-name email person-id]} :user} _]
  (let [roles (user-roles conn id)]
    {:token (login-api-token/create-token (environment/config-value :auth :jwt-secret) "teet_user"
                                          {:given-name given-name
                                           :family-name family-name
                                           :person-id person-id
                                           :email email
                                           :id id
                                           :roles roles})
     :roles roles}))

(defmethod db-api/command-authorization :refresh-token [{user :user} _]
  (when-not (and user
                 (every? #(contains? user %)
                         [:user/id :user/given-name :user/family-name :user/person-id]))
    (throw (ex-info "Can't refresh token, user information missing"
                   {:user user}))))

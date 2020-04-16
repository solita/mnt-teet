(ns teet.login.login-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.environment :as environment]
            [teet.auth.jwt-token :as jwt-token]
            [datomic.client.api :as d]
            [teet.log :as log]
            [teet.user.user-db :as user-db]))


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



(defcommand :login/login
  {:doc "Login to the system"
   :context {conn :conn}
   :payload {:user/keys [id given-name family-name email person-id]
             site-password :site-password}
   :unauthenticated? true}
  (d/transact conn {:tx-data [{:user/id id}]})

  (when-not (environment/feature-enabled? :dummy-login)
    (log/warn "Demo login can only be used in dev environment")
    (throw (ex-info "Demo login not allowed"
                    {:demo-user id})))

  (if (environment/check-site-password site-password)
    (let [secret (environment/config-value :auth :jwt-secret)
          db (d/db conn)
          roles (user-db/user-roles db [:user/id id])]
      {:token (jwt-token/create-token secret "teet_user"
                                      {:given-name given-name
                                       :family-name family-name
                                       :person-id person-id
                                       :email email
                                       :id id
                                       :roles roles})
       :user (user-db/user-info db [:user/id id])
       :roles roles
       :enabled-features (environment/config-value :enabled-features)
       :api-url (environment/config-value :api-url)})
    {:error :incorrect-site-password}))


(defn on-tara-login [claims]
  (let [conn (environment/datomic-connection)
        secret (environment/config-value :auth :jwt-secret)

        ;; Destructure person info from claims
        {person-id "sub"
         {:strs [given_name family_name]} "profile_attributes"} claims

        id (ensure-user! conn person-id given_name family_name)
        db (d/db conn)
        roles (user-db/user-roles db [:user-id id])
        response {:status 302
                  :headers {"Location"
                            (str (environment/config-value :base-url)
                                 "#/login"
                                 (if (empty? roles)
                                   "?error=no-roles"
                                   (str "?token="
                                        (jwt-token/create-token
                                         secret "teet_user"
                                         {:given-name given_name
                                          :family-name family_name
                                          :person-id person-id
                                          :id id
                                          :roles roles}))))}
                  :body "Redirecting to TEET"}]
    (log/info "on-tara-login response: " response)
    response))

(defcommand :login/check-session-token
  {:doc "Check for JWT token in cookie session"
   :context {session :session}
   :payload _
   :unauthenticated? true}
  (:jwt-token session))

(defcommand :login/refresh-token
  {:doc "Refresh JWT token for existing session"
   :context {db :db
             {:user/keys [id given-name family-name email person-id] :as user} :user}
   :pre [(and user
              (every? #(contains? user %)
                      [:user/id :user/given-name :user/family-name :user/person-id]))]
   :payload _
   :project-id nil
   :authorization {}}
  (let [roles (user-db/user-roles db [:user/id id])]
    {:token (jwt-token/create-token (environment/config-value :auth :jwt-secret) "teet_user"
                                    {:given-name given-name
                                     :family-name family-name
                                     :person-id person-id
                                     :email email
                                     :id id
                                     :roles roles})
     :user (user-db/user-info db [:user/id id])
     :roles roles
     :enabled-features (environment/config-value :enabled-features)
     :api-url (environment/config-value :api-url)}))

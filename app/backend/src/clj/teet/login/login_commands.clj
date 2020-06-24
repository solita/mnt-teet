(ns teet.login.login-commands
  (:require [clojure.spec.alpha :as s]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.environment :as environment]
            [teet.auth.jwt-token :as jwt-token]
            [datomic.client.api :as d]
            [teet.log :as log]
            [teet.user.user-db :as user-db]))


(defn- new-user-tx [person-id given-name family-name]
  {:user/id (java.util.UUID/randomUUID)
   :user/person-id person-id
   :user/given-name given-name
   :user/family-name family-name})

(defn- add-user!
  "Transacts a new user to Datomic. Returns the user's UUID."
  [conn person-id given-name family-name]
  (let [tx (new-user-tx person-id given-name family-name)]
    (d/transact conn {:tx-data [tx]})
    (:user/id tx)))

(defn- update-user-tx
  "Returns transaction data for updating the user's given name or family
  name if they don't match those found in the claims."
  [{db-given-name :user/given-name
    db-family-name :user/family-name
    user-id :user/id}
   given-name family-name]
  (when-let [changes (merge nil
                            (when (not= db-given-name given-name)
                              {:user/given-name given-name})
                            (when (not= db-family-name family-name)
                              {:user/family-name family-name}))]
    (assoc changes :user/id user-id)))

(defn- ensure-user-data!
  "Updates user's given name or family name if they're not up to date
  with those in the claims."
  [conn user-in-db given-name family-name]
  (when-let [tx (update-user-tx user-in-db given-name family-name)]
    (log/info "Updating user data in db to match the data in claims")
    (d/transact conn {:tx-data [tx]})))

(defn ensure-user!
  "Make sure that the given user exists in the database.
  If user does not exist, creates it. If the data from claims doesn't
  match the data in db, the db data is updated.

  Returns user id (uuid)."

  [conn person-id given-name family-name]
  (log/info "Ensure user: " person-id given-name family-name)
  (let [user-in-db (-> conn d/db
                       (d/pull '[:user/id :user/given-name :user/family-name]
                               [:user/person-id person-id]))]
    (if-let [user-id (:user/id user-in-db)]
      (do (ensure-user-data! conn user-in-db given-name family-name)
          user-id)
      (add-user! conn person-id given-name family-name))))



;; dummy-login trust person-id etc information from
;; the frontend (command could be renamed to dummy-login)

(defcommand :login/login
  {:doc "Login to the system"
   :context {conn :conn}
   :payload {:user/keys [id given-name family-name email person-id]
             site-password :site-password}
   :spec (s/keys :req [:user/id :user/given-name :user/family-name :user/email :user/person-id]
                 :opt-un [::site-password])
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


;; production env login, teet.db-api.db-api-ion/tara-login
;; will eventually send us here.
;; the redirect url is handled in the frontend routing.

(defn on-tara-login [claims]
  (let [conn (environment/datomic-connection)
        secret (environment/config-value :auth :jwt-secret)

        ;; Destructure person info from claims
        {person-id "sub"
         {:strs [given_name family_name]} "profile_attributes"} claims

        id (ensure-user! conn person-id given_name family_name)
        db (d/db conn)
        roles (user-db/user-roles db [:user/id id])
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
   :spec empty?
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
   :spec empty?
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

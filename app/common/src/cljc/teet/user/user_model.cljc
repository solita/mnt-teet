(ns teet.user.user-model
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.util.date :refer [date-after?]]
            [teet.user.user-spec :as user-spec]))

(def user-display-attributes
  [:user/given-name :user/family-name])

(def user-listing-attributes
  [:db/id
   :user/id
   :user/given-name
   :user/family-name])

(def user-info-attributes
  (into user-listing-attributes
        [:user/email]))

(defn user-name
  "Show full user name"
  [{:user/keys [given-name family-name]}]
  (when (or given-name family-name)
    (str given-name " " family-name)))

(defn user-name-and-email
  "Show user name and email"
  [{:user/keys [email] :as user}]
  (str (user-name user)
       (when email
         (str " (" email ")"))))

(defn user-ref
  "Returns a user reference suitable for a datomic ref value.
  User may be a user reference (eid) or a user entity map."
  [user]
  (cond
    ;; Valid user reference, return as is
    (s/valid? :user/eid user)
    user

    (and (map? user)
         (contains? user :db/id))
    (:db/id user)

    ;; This is a user entity map, return user uuid
    (and (map? user)
         (contains? user :user/id))
    [:user/id (:user/id user)]

    (and (map? user)
         (contains? user :user/person-id))
    [:user/person-id (:user/person-id user)]

    ;; Not valid user
    :else
    (throw (ex-info "Not a valid user reference. Expected eid or user info map."
                    {:invalid-user-ref user}))))


(defn permissions-valid-at
  "Returns the user permissions that are valid during the given time."
  [user timestamp]
  (->> user
       :user/permissions
       (filter (fn [{:permission/keys [valid-from valid-until]}]
                 (and valid-from
                      (not (date-after? valid-from timestamp))
                      ;; If valid-until exists, it must not be before the timestamp
                      (not (and valid-until
                                (date-after? timestamp valid-until))))))))

(defn- permission->projects
  [{:permission/keys [role projects]}]
  (map #(-> %
            (select-keys [:db/id])
            (assoc :permission/role role))
       projects))

(defn projects-with-valid-permission-at
  [user timestamp]
  (->> (permissions-valid-at user timestamp)
       (mapcat permission->projects)
       (remove nil?)))

#?(:clj (defn new-user [person-id]
          {:db/id "new-user"
           :user/id (java.util.UUID/randomUUID)
           :user/person-id person-id}))

(defn normalize-person-id
  "Normalizes the `person-id` to contain the 'EE' prefix."
  [person-id]
  {:pre [(user-spec/estonian-person-id? person-id)]}
  (if (str/starts-with? person-id "EE")
    person-id
    (str "EE" person-id)))

(ns teet.user.user-model
  (:require teet.user.user-spec
            [clojure.spec.alpha :as s]))

(def user-listing-attributes
  [:user/id
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

    ;; This is a user entity map, return user uuid
    (and (map? user)
         (contains? user :user/id))
    [:user/id (:user/id user)]

    ;; Not valid user
    :else
    (throw (ex-info "Not a valid user reference. Expected eid or user info map."
                    {:invalid-user-ref user}))))

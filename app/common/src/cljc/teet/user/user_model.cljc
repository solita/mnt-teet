(ns teet.user.user-model)

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
  (str given-name " " family-name))

(defn user-name-and-email
  "Show user name and email"
  [{:user/keys [email] :as user}]
  (str (user-name user)
       (when email
         (str " (" email ")"))))

(ns teet.user.user-model)

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

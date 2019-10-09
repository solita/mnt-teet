(ns teet.user.user-info
  "Show user info in the UI, automatically resolved user information"
  (:require [clojure.string :as str]))

(def mock-users [{:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"
                  :user/person-id "1234567890"
                  :user/given-name "Danny D."
                  :user/family-name "Manager"
                  :user/email "danny.d.manager@example.com"
                  :user/organization "Maanteeamet"}

                 {:user/id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271"
                  :user/person-id "3344556677"
                  :user/given-name "Carla"
                  :user/family-name "Consultant"
                  :user/email "carla.consultant@example.com"
                  :user/organization "ACME Road Consulting, Ltd."}

                 {:user/id #uuid "fa8af5b7-df45-41ba-93d0-603c543c880d"
                  :user/person-id "9483726473"
                  :user/given-name "Benjamin"
                  :user/family-name "Boss"
                  :user/email "benjamin.boss@example.com"
                  :user/organization "Maanteeamet"}])

(defn- user-by-id [id]
  (some #(when (= (:user/id %) id) %) mock-users))

(defn user-name
  "Show full user name"
  [_e! user-id]
  (->> user-id
       user-by-id
       ((juxt :user/given-name :user/family-name))
       (str/join " ")))

(defn user-name-and-email
  "Show user name and email"
  [_e! user-id]
  (let [[gn fn email] (->> user-id
                           user-by-id
                           ((juxt :user/given-name :user/family-name :user/email)))]
    (str gn " " fn " (" email ")")))

(defn list-user-ids []
  (map :user/id mock-users))

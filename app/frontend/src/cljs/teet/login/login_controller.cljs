(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]
            [teet.login.login-paths :as login-paths]))

(defrecord Login [user])
(defrecord SetToken [token])

(extend-protocol t/Event
  Login
  (process-event [{user :demo-user} app]
    (log/info "Log in as: " user)
    (t/fx (-> app
              (assoc-in [:login :progress?] true)
              (assoc :user user))
          {::tuck-effect/type :command!
           :command :login
           :payload user
           :result-event ->SetToken}))

  SetToken
  (process-event [{token :token} app]
    (log/info "TOKEN: " token)
    (t/fx (assoc-in app login-paths/api-token token)
          {::tuck-effect/type :set-api-token
           :token token}
          {::tuck-effect/type :navigate
           :page :projects})))

(def mock-users [{:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"
                  :user/person-id "1234567890"
                  :user/given-name "Danny"
                  :user/family-name "Design-Manager"
                  :user/email "danny.design-manager@example.com"
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

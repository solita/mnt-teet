(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]
            [teet.login.login-paths :as login-paths]))

(defrecord Login [demo-user])
(defrecord SetToken [after-login? token])
(defrecord RefreshToken [])

(def ^{:const true
       :doc "How often to refresh JWT token (15 minutes)"}
  refresh-token-timeout-ms (* 1000 60 15))

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
           :result-event (partial ->SetToken true)}))

  SetToken
  (process-event [{:keys [token after-login?]} app]
    (log/info "TOKEN: " token ", after-login? " after-login?)
    (let [effects [{::tuck-effect/type :set-api-token
                    :token token}
                   {::tuck-effect/type :debounce
                    :id :refresh-token
                    :timeout refresh-token-timeout-ms
                    :event ->RefreshToken}]
          effects (if after-login?
                    (conj effects {::tuck-effect/type :navigate
                                   :page :projects})
                    effects)]
      (apply t/fx (assoc-in app login-paths/api-token token)
             effects)))

  RefreshToken
  (process-event [_ app]
    (t/fx app
          {::tuck-effect/type :command!
           :command :refresh-token
           :payload {}
           :result-event (partial ->SetToken false)})))

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

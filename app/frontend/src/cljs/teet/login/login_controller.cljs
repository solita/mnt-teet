(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]
            [teet.login.login-paths :as login-paths]
            [teet.user.user-info :as user-info]))

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

(def mock-users user-info/mock-users)

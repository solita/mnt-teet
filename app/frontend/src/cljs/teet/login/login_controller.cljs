(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]
            [teet.login.login-paths :as login-paths]
            [teet.user.user-info :as user-info]
            teet.user.user-controller))

(defrecord Login [demo-user])
(defrecord SetToken [after-login? navigate-data token])
(defrecord SetPassword [pw])
(defrecord RefreshToken [])

(def ^{:const true
       :doc "How often to refresh JWT token (15 minutes)"}
  refresh-token-timeout-ms (* 1000 60 15))

(extend-protocol t/Event
  Login
  (process-event [{user :demo-user} app]
    (log/info "Log in as: " user " site pw: "(get-in app [:login :password]))
    (let [navigate-data (get-in app [:login :navigate-to])]
      (t/fx (-> app
                (assoc-in [:login :progress?] true)
                (assoc :user user))
            {::tuck-effect/type :command!
             :command :login
             :payload (assoc user :site-password (get-in app [:login :password]))
             :result-event (partial ->SetToken true navigate-data)})))

  SetPassword
  (process-event [{:keys [pw]} app]
    (t/fx (assoc-in app [:login :password] pw)))

  SetToken
  (process-event [{:keys [token after-login? navigate-data]} app]
    (log/info "TOKEN: " token ", after-login? " after-login?)
    (let [{:keys [token error roles]} token]
      (if error
        (do (js/alert (str "Login error: " (str error)))
            app)
        (let [effects [{::tuck-effect/type :set-api-token
                        :token token}
                       {::tuck-effect/type :set-user-roles
                        :roles roles}
                       {::tuck-effect/type :debounce
                        :id :refresh-token
                        :timeout refresh-token-timeout-ms
                        :event ->RefreshToken}]
              effects (if after-login?
                        (conj effects (merge {::tuck-effect/type :navigate
                                              :page :projects}
                                             navigate-data))
                        effects)]
          (apply t/fx
                 (-> app
                     (assoc-in login-paths/api-token token)
                     (assoc-in [:login :progress?] false))
                 effects)))))

  RefreshToken
  (process-event [_ app]
    (t/fx app
          {::tuck-effect/type :command!
           :command :refresh-token
           :payload {}
           :result-event (partial ->SetToken false nil)})))

(def mock-users user-info/mock-users)

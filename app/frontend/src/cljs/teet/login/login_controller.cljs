(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [teet.log :as log]
            [teet.login.login-paths :as login-paths]
            [teet.user.user-info :as user-info]
            teet.user.user-controller
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord Login [demo-user])
(defrecord SetSessionInfo [after-login? navigate-data token])
(defrecord SetPassword [pw])
(defrecord RefreshToken [])
(defrecord CheckSessionToken [])

(def ^{:const true
       :doc "How often to refresh JWT token (15 minutes)"}
  refresh-token-timeout-ms (* 1000 60 15))

(extend-protocol t/Event
  CheckSessionToken
  (process-event [_ app]
    (if-let [token (get-in app [:query :token])]
      (t/fx app
            ;; Set the JWT token and immediately refresh
            ;; to get other session info
            {::tuck-effect/type :set-api-token
             :token token}

            {::tuck-effect/type :command!
             :command :refresh-token
             :payload {}
             :result-event (partial ->SetSessionInfo true (get-in app [:login :navigate-to]))})
      (if-let [error (get-in app [:query :error])]
        (snackbar-controller/open-snack-bar app (str "Login failed: " error) :error)
        app)))

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
             :result-event (partial ->SetSessionInfo true navigate-data)})))

  SetPassword
  (process-event [{:keys [pw]} app]
    (t/fx (assoc-in app [:login :password] pw)))

  SetSessionInfo
  (process-event [{:keys [token after-login? navigate-data]} app]
    (log/info "TOKEN: " token ", after-login? " after-login?)
    (let [{:keys [token error roles user enabled-features]} token]
      (if error
        (do (js/alert (str "Login error: " (str error)))
            app)
        (let [effects [{::tuck-effect/type :set-api-token
                        :token token}
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
                     (assoc :user (assoc user :roles roles))
                     (assoc :enabled-features enabled-features)
                     (assoc-in login-paths/api-token token)
                     (assoc-in [:login :progress?] false))
                 effects)))))

  RefreshToken
  (process-event [_ app]
    (t/fx app
          {::tuck-effect/type :command!
           :command :refresh-token
           :payload {}
           :result-event (partial ->SetSessionInfo false nil)})))

(def mock-users user-info/mock-users)

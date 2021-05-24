(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [teet.log :as log]
            [teet.login.login-paths :as login-paths]
            [teet.user.user-info :as user-info]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.common.common-controller :as common-controller]
            [teet.app-state :as app-state]
            [clojure.string :as str]
            [teet.ui.query :as query]))

(defrecord Login [demo-user])
(defrecord Logout [])
(defrecord SetSessionInfo [after-login? session-info])
(defrecord SetPassword [pw])
(defrecord RefreshToken [])
(defrecord CheckSessionToken [])
(defrecord CheckExistingSession [])
(defrecord CheckSessionError [])

(def ^{:const true
       :doc "How often to refresh JWT token (15 minutes)"}
  refresh-token-timeout-ms (* 1000 60 15))

;; Events to run after session has been initialized/user has logged in (has a valid jwt)
(defonce init-events (atom {:query-request-permissions #(query/->Query :authorization/permissions
                                                                       {}
                                                                       [:authorization/permissions]
                                                                       nil)}))
(defn register-init-event!
  "Register an init event to be run when user has logged in."
  [name constructor]
  (swap! init-events assoc name constructor))

(defn run-init-events!
  "Run all registered init events."
  [e!]
  (doseq [[name constructor] @init-events]
    (log/info "Run init event: " name)
    (e! (constructor)))
  ;; Clear init events
  (reset! init-events nil))

(extend-protocol t/Event
  CheckExistingSession ; Check existing token from browser localstorage storage
  (process-event [_ app]
    (let [app (assoc app :initialized? true)]
      (when (not= (:page app) :login)
        (reset! common-controller/navigation-data-atom (select-keys app [:page :params :query])))
      (if @common-controller/api-token
        (t/fx (assoc app :checking-session? true)
              {::tuck-effect/type :command!
               :command           :login/refresh-token
               :payload           {}
               :error-event       ->CheckSessionError
               :result-event      (partial ->SetSessionInfo false)})
        (t/fx app
              {::tuck-effect/type :navigate
               :page :login}))))

  CheckSessionError
  (process-event [_ app]
    (reset! common-controller/api-token nil)
    (t/fx (-> app
              (assoc :checking-session? false)
              (snackbar-controller/open-snack-bar "Login session timed out" :warning))
          {::tuck-effect/type :navigate
           :page :login}))

  CheckSessionToken ; Get token from cookie session when logging in
  (process-event [_ app]
    (if-let [token (get-in app [:query :token])]
      (t/fx app
            ;; Set the JWT token and immediately refresh
            ;; to get other session info
            {::tuck-effect/type :set-api-token
             :token token}

            {::tuck-effect/type :command!
             :command :login/refresh-token
             :payload {}
             :result-event (partial ->SetSessionInfo true)})
      (if-let [error (get-in app [:query :error])]
        (snackbar-controller/open-snack-bar app (str "Login failed: " error) :error)
        app)))

  Login
  (process-event [{user :demo-user} app]
    (log/info "Log in as: " user " site pw: "(get-in app [:login :password]))
    (t/fx (-> app
              (assoc-in [:login :progress?] true)
              (assoc :user user))
          {::tuck-effect/type :command!
           :command :login/login
           :payload (assoc user :site-password (get-in app [:login :password]))
           :result-event (partial ->SetSessionInfo true)}))

  Logout
  (process-event [_ app]
    (t/fx app
          {::tuck-effect/type :clear-api-token
           :token nil}))

  SetPassword
  (process-event [{:keys [pw]} app]
    (t/fx (assoc-in app [:login :password] pw)))

  SetSessionInfo
  (process-event [{:keys [session-info after-login?]} app]
    (log/info "TOKEN: " session-info ", after-login? " after-login?)
    (let [navigation-data @common-controller/navigation-data-atom
          {:keys [token error roles user enabled-features config]} session-info
          app (assoc app :initialized? true :checking-session? false)]
      (reset! common-controller/navigation-data-atom nil)
      (if error
        (do (js/alert (str "Login error: " (str error)))
            app)
        (let [effects [{::tuck-effect/type :set-api-token
                        :token token}
                       {::tuck-effect/type :debounce
                        :id :login/refresh-token
                        :timeout refresh-token-timeout-ms
                        :event ->RefreshToken}
                       (fn [e!]
                         (run-init-events! e!))]
              effects (if after-login?
                        (conj effects (merge {::tuck-effect/type :navigate
                                              :page :projects}
                                             (when (and (not= :login (:page navigation-data))
                                                        (:page navigation-data))
                                               navigation-data)))
                        effects)]
          (apply t/fx
                 (-> app
                     (assoc :config config)
                     (assoc :user (assoc user :roles roles))
                     (assoc :enabled-features enabled-features)
                     (assoc-in login-paths/api-token token)
                     (assoc-in [:login :progress?] false))
                 effects)))))

  RefreshToken
  (process-event [_ app]
    (t/fx app
          {::tuck-effect/type :command!
           :command :login/refresh-token
           :payload {}
           :result-event (partial ->SetSessionInfo false)})))

(def mock-users user-info/mock-users)

(defn ^:export test-login
  "Login with existing mock user"
  [site-password mock-user-name]
  (swap! app-state/app assoc-in [:login :password] site-password)
  (let [e! (t/control app-state/app)
        user (first
              (for [{:user/keys [given-name family-name] :as user} mock-users
                    :let [name (str given-name " " family-name)]
                    :when (str/includes? (str/lower-case name)
                                         (str/lower-case mock-user-name))]
                user))]
    (when user
      (e! (->Login user))
      (str "Login as " user))))

(defn ^:export test-login-with-token
  "Login with existing JWT token"
  [token]
  (swap! app-state/app assoc-in [:query :token] token)
  (let [e! (t/control app-state/app)]
    (e! (reify t/Event
          (process-event [_ app]
            (t/fx app
                  {:tuck.effect/type :set-api-token
                   :token token}
                  {:tuck.effect/type :command!
                   :command :login/refresh-token
                   :payload {}
                   :result-event (partial ->SetSessionInfo false)}))))))

(ns teet.test-setup
  "Code to setup things in Datomic for Cypress testing."
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [compojure.core :refer [routes POST]]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.core.async :refer [thread timeout <!!]]
            [teet.util.collection :as cu]
            [teet.auth.jwt-token :as jwt-token]
            [teet.link.link-queries :as link-queries]
            [teet.util.string :as string]
            [teet.log :as log]))

(defn- read-json-body [req]
  (-> req :body io/reader
      (cheshire/decode-stream keyword)))

(def ^:const one-day-ms (* 1000 60 60 24))

(defn- project->lifecycle->activity->task
  [payload]
  (let [now (System/currentTimeMillis)
        start (java.util.Date. (- now one-day-ms))
        end (java.util.Date. (+ now (* 100 one-day-ms)))
        {:keys [project-id project-name project-cadastral-units
                road activity group task]
         :or {project-id (str now)
              project-name (str "testproject " now)
              activity "pre-design"
              group "study"
              task "feasibility-study"
              road [1 1 100 15000]}} payload
        [road-nr carriageway start-m end-m] road]
    (merge
     (when project-cadastral-units
       {:thk.project/related-cadastral-units project-cadastral-units})
     {:db/id "project"
      :thk.project/id project-id
      :thk.project/name project-name
      :thk.project/road-nr road-nr
      :thk.project/carriageway carriageway
      :thk.project/start-m start-m
      :thk.project/end-m end-m
      :thk.project/estimated-start-date start
      :thk.project/estimated-end-date end
      :thk.project/lifecycles
      [{:db/id "lifecycle"
        :thk.lifecycle/type :thk.lifecycle-type/design
        :thk.lifecycle/estimated-start-date start
        :thk.lifecycle/estimated-end-date end
        :thk.lifecycle/activities
        [{:db/id "activity"
          :activity/name (keyword "activity.name" activity)
          :activity/estimated-start-date start
          :activity/estimated-end-date end
          :activity/tasks
          [{:db/id "task"
            :task/group (keyword "task.group" group)
            :task/type (keyword "task.type" task)
            :task/estimated-start-date start
            :task/estimated-end-date end}]}]}]})))

(defn- retract-all [tempids]
  (thread
    ;; Cleanup: retract all created entities after 2 minutes
    (<!! (timeout (* 1000 120)))

    (d/transact (environment/datomic-connection)
                {:tx-data (for [[_ id] tempids]
                            [:db/retractEntity id])})))

(defn- setup-task [req]
  (let [;; Create project, lifecycle, activity and task
        proj (project->lifecycle->activity->task (read-json-body req))
        {tempids :tempids}
        (d/transact
         (environment/datomic-connection)
         {:tx-data [proj]})]
    ;; Setup cleanup to run
    (retract-all tempids)

    ;; Return ids and URL to task
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            (merge tempids
                   {:project-id (:thk.project/id proj)
                    :task-url (str "#/projects/" (:thk.project/id proj)
                                   "/" (tempids "activity")
                                   "/" (tempids "task"))}))}))

(defn- user [{:keys [given-name family-name person-id email role]}]
  (cu/without-nils
   {:db/id "user"
    :user/id (java.util.UUID/randomUUID)
    :user/email email
    :user/given-name given-name
    :user/family-name family-name
    :user/person-id person-id
    :user/permissions [{:db/id "permission"
                        :permission/role (keyword role)
                        :permission/valid-from (java.util.Date.)}]}))

(defn- setup-user [req]
  (let [u (user (read-json-body req))
        {tempids :tempids}
        (d/transact (environment/datomic-connection)
                    {:tx-data [u]})]
    (retract-all tempids)

    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            (merge tempids
                   {:token (jwt-token/create-token (environment/config-value :auth :jwt-secret)
                                                   "teet_user"
                                                   {:given-name (:user/given-name u)
                                                    :family-name (:user/family-name u)
                                                    :person-id (:user/person-id u)
                                                    :email (:user/email u)
                                                    :id (:user/id u)})}))}))


;; Cadastral unit results for tests
(def cadastral-unit-results
  [{:KINNISTU "5477850",
    :L_AADRESS "58 Aluste-Kergu tee",
    :TUNNUS "12601:004:0003",
    :link/external-id "2:12601:004:0003",
    :link/type :cadastral-unit}
   {:KINNISTU "5164950",
    :L_AADRESS "58 Aluste-Kergu tee",
    :TUNNUS "12601:001:0087",
    :link/external-id "2:12601:001:0087",
    :link/type :cadastral-unit}
   {:KINNISTU "5453150",
    :L_AADRESS "58 Aluste-Kergu tee"
    :TUNNUS "12601:004:0004",
    :link/external-id "2:12601:004:0004",
    :link/type :cadastral-unit}
   {:KINNISTU "3515350",
    :L_AADRESS "Juurdepääsutee lõik 471",
    :TUNNUS "12601:001:0093",
    :link/external-id "2:12601:001:0093",
    :link/type :cadastral-unit}
   {:KINNISTU "3515450",
    :L_AADRESS "Rapla-Pärnu raudtee 471",
    :TUNNUS "12601:001:0050",
    :link/external-id "2:12601:001:0050",
    :link/type :cadastral-unit}
   {:KINNISTU "12814150",
    :L_AADRESS "Tartu maantee",
    :TUNNUS "12601:001:0193",
    :link/external-id "2:12601:001:0193",
    :link/type :cadastral-unit}])


(defn setup-mock-cadastral-unit-link-search [_req]
  (defmethod link-queries/search-link :cadastral-unit
    [_db _user _config _project _ _lang text]
    (log/debug "Mock cadastral unit search, text: " text)
    (filterv #(or (string/contains-words? (:TUNNUS %) text)
                    (string/contains-words? (:L_AADRESS %) text))
             cadastral-unit-results))

  {:status 200 :body "ok"})

(defn test-setup-routes []
  (routes
   (POST "/testsetup/:setup" [setup :as req]
         (let [setup-fn (->> setup
                             (str "setup-")
                             (symbol "teet.test-setup")
                             resolve
                             deref)]
           (assert (fn? setup-fn))
           (setup-fn req)))))

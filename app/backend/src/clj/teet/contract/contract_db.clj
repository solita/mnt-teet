(ns teet.contract.contract-db
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du])
  (:import (java.util Date)))

(def contract-query-rules
  '[[(project-name-matches? ?project ?name-search-value)
     [?project :thk.project/name ?thk-name]
     [(get-else $ ?project :thk.project/project-name ?thk-name) ?project-name]
     [(.toLowerCase ^String ?project-name) ?lower-project-name]
     [(teet.util.string/contains-words? ?lower-project-name ?name-search-value)]]

    [(contract-target-activity ?c ?activity)
     [?c :thk.contract/targets ?activity]
     [?activity :activity/name _]]

    [(contract-target-activity ?c ?activity)
     [?c :thk.contract/targets ?target]
     [?activity :activity/tasks ?target]]

    [(target-activity ?target ?activity)
     [?target :activity/name _]
     [?activity :activity/name _]
     [(= ?target ?activity)]]

    [(target-activity ?target ?activity)
     [?activity :activity/tasks ?target]]

    [(contract-target-project ?c ?project)
     [?c :thk.contract/targets ?target]
     (or-join [?target ?project]
              (and
                [?activity :activity/tasks ?target]
                [?lc :thk.lifecycle/activities ?activity]
                [?project :thk.project/lifecycles ?lc])
              (and
                [?lc :thk.lifecycle/activities ?target]
                [?project :thk.project/lifecycles ?lc]))]

    [(target-project ?target ?project)
     (or-join [?target ?project]
              (and
                [?activity :activity/tasks ?target]
                [?lc :thk.lifecycle/activities ?activity]
                [?project :thk.project/lifecycles ?lc])
              (and
                [?lc :thk.lifecycle/activities ?target]
                [?project :thk.project/lifecycles ?lc]))]])


(def contract-status-rules
  '[[(contract-deadline ?c ?act-dl)
     [?c :thk.contract/deadline ?act-dl]
     [(missing? $ ?c :thk.contract/extended-deadline)]]
    [(contract-deadline ?c ?act-dl)
     [?c :thk.contract/extended-deadline ?act-dl]]
    ;; Signed - when start of work is not given
    [(contract-status ?c ?s ?now)
     [(missing? $ ?c :thk.contract/start-of-work)]
     [(ground :thk.contract.status/signed) ?s]]
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start-of-work]
     [(< ?now ?start-of-work)]
     [(ground :thk.contract.status/signed) ?s]]
    ;In progress - start of work given and that date has passed and deadline > 30
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [(< ?start ?now)]
     (or-join [?c ?now ?act-dl]
              (and
                (contract-deadline ?c ?act-dl)
                [(teet.util.date/dec-days ?act-dl 30) ?deadline-soon]
                [(< ?now ?deadline-soon)])
              (and
                [(missing? $ ?c :thk.contract/deadline)]
                [(missing? $ ?c :thk.contract/actual-deadline)]))
     [(ground :thk.contract.status/in-progress) ?s]]
    ;Deadline approaching - Days until deadline < 30 deadline is given
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     (contract-deadline ?c ?act-dl)
     [(teet.util.date/dec-days ?act-dl 30) ?deadline-soon]
     [(> ?act-dl ?now)]
     [(> ?now ?deadline-soon)]
     [(ground :thk.contract.status/deadline-approaching) ?s]]
    ;Deadline overdue - Deadline date has passed
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [(missing? $ ?c :thk.contract/warranty-end-date)]
     (contract-deadline ?c ?act-dl)
     [(< ?act-dl ?now)]
     [(ground :thk.contract.status/deadline-overdue) ?s]]
    ;Warranty - deadline (and extended deadline if given) has passed and warranty date isn't passed
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [?c :thk.contract/warranty-end-date ?warranty-end]
     [(< ?now ?warranty-end)]
     (contract-deadline ?c ?act-dl)
     [(< ?act-dl ?now)]
     [(ground :thk.contract.status/warranty) ?s]]
    ;Completed - deadline and warranty period passed.
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/warranty-end-date ?warranty-end]
     (contract-deadline ?c ?act-dl)
     [(< ?act-dl ?now)]
     [(< ?warranty-end ?now)]
     [(ground :thk.contract.status/completed) ?s]]])

(defn contract-with-status
  "Used after datomic query which returns [contract contract-status]"
  [[contract status]]
  (assoc contract :thk.contract/status status))

(defn get-contract
  [db contract-eid]
  (-> (d/q '[:find (pull ?c [*]) ?status
             :where
             (contract-status ?c ?status ?now)
             :in $ % ?c ?now]
           db
           contract-status-rules
           contract-eid
           (Date.))
      first
      contract-with-status))

(defn- format-target-information
  [[target project activity]]
  (let [project-id (:thk.project/id project)
        activity-id (str (:db/id activity))]
    {:target target
     :activity {:activity/manager (user-model/user-name (:activity/manager activity))
                :activity/name (:activity/name activity)}
     :project project
     :target-navigation-info (if (:activity/name target)
                               {:page :activity
                                :params {:project project-id
                                         :activity activity-id}}
                               {:page :activity-task
                                :params {:project project-id
                                         :activity activity-id
                                         :task (str (:db/id target))}})}))

(defn contract-target-information
  [db contract-eid]
  (->> (d/q '[:find
              (pull ?target [*])
              (pull ?project [:thk.project/id :thk.project/name :thk.project/project-name])
              (pull ?activity [:db/id :activity/name
                               {:thk.lifecycle/_activities [:thk.lifecycle/id]}
                               {:activity/manager [:user/family-name :user/given-name]}])
              :where
              [?c :thk.contract/targets ?target]
              (target-activity ?target ?activity)
              (target-project ?target ?project)
              :in $ % ?c]
            db
            contract-query-rules
            contract-eid)
       (mapv format-target-information)
       du/idents->keywords))

(defn project-related-contracts
  [db project-eid]
  (->> (d/q '[:find (pull ?c [:thk.contract/procurement-id+procurement-part-id :thk.contract/status
                              :thk.contract/name :thk.contract/part-name :db/id]) ?status
              :where
              (contract-target-project ?c ?project)
              (contract-status ?c ?status ?now)
              :in $ % ?project ?now]
            db
            (into contract-query-rules
                  contract-status-rules)
            project-eid
            (Date.))
       (mapv contract-with-status)))

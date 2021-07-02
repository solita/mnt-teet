(ns teet.contract.contract-db
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            [teet.company.company-model :as company-model]
            [teet.user.user-queries :as user-queries])
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
     (or-join [?c ?now]
              (and (contract-deadline ?c ?act-dl)
                   [(teet.util.date/dec-days ?act-dl 30) ?deadline-soon]
                   [(> ?act-dl ?now)]
                   [(< ?now ?deadline-soon)])
              (and [(missing? $ ?c :thk.contract/deadline)]
                   [(missing? $ ?c :thk.contract/extended-deadline)]))
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
                [(missing? $ ?c :thk.contract/extended-deadline)]))
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

(def contract-partner-attributes
  (let [company-with-meta-keys (into []
                                 (concat
                                   company-model/company-keys
                                   [:meta/created-at
                                    :meta/modified-at
                                    {:meta/modifier [:user/id :user/family-name :user/given-name]}
                                    {:meta/creator [:user/id :user/family-name :user/given-name]}]))
        pull-attributes `[~'*
                          {:company-contract/_contract
                           [~'*
                            {:company-contract/company ~company-with-meta-keys}]}]]
    pull-attributes))

(defn get-contract-with-partners
  [db contract-eid]
  (-> (d/q '[:find (pull ?c contract-partners-attributes) ?status
             :where
             (contract-status ?c ?status ?now)
             :in $ % ?c ?now contract-partners-attributes]
        db
        contract-status-rules
        contract-eid
        (Date.)
        contract-partner-attributes)
    first
    contract-with-status))

(defn contract-query
  [db contract-eid]
  (d/q '[:find (pull ?c [*]) ?status
         :where
         (contract-status ?c ?status ?now)
         :in $ % ?c ?now]
       db
       contract-status-rules
       contract-eid
       (Date.)))

(defn get-contract
  [db contract-eid]
  (let [query-result (contract-query db contract-eid)
        query-count (count query-result)]
    (assert (= 1 query-count) (str "Contract status matches more than 1 rule, matched: " query-count))
    (-> query-result
        first
        contract-with-status)))

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

(defn contract-lead-partner-entities
  "Fetch contracts lead partner company id and contract-company id"
  [db contract-id]
  (->> (d/q '[:find ?company ?cc
              :where
              [?cc :company-contract/contract ?contract]
              [?cc :company-contract/lead-partner? true]
              [?cc :company-contract/company ?company]
              :in $ ?contract]
            db contract-id)
       first))

(defn get-lead-partner-company [db contract-id]
  (let [lead-partner-entities (contract-lead-partner-entities db contract-id)]
    (when (some? lead-partner-entities)
          (d/pull db '[*]
               (first lead-partner-entities)))))

(defn contract-with-lead-partner
  "Can be used in contract card to show only Lead partner"
  [db contract]
  (let [contract-eid (:thk.contract/procurement-id+procurement-part-id contract)
        lead-partner (get-lead-partner-company db [:thk.contract/procurement-id+procurement-part-id contract-eid])]
    (assoc contract :thk.contract/lead-partner lead-partner)))

(defn contract-with-all-partners
  "Used in contract card to show all partners"
  [db contract]
  (let [contract-eid (:thk.contract/procurement-id+procurement-part-id contract)
        partners (get-contract-with-partners db
                   [:thk.contract/procurement-id+procurement-part-id contract-eid])]
    (assoc contract :thk.contract/contract-with-partners partners)))

(defn contract-partner-relation-entity-uuid
  "Fetch company-contract entity uuid"
  [db company-id contract-id]
  (->> (d/q '[:find ?t
              :where
              [?cc :teet/id ?t]
              [?cc :company-contract/contract ?contract]
              [?cc :company-contract/company ?company]
              :in $ ?contract ?company]
         db contract-id company-id)
    ffirst))

(defn is-company-contract-employee?
  "Given user id and company-contract check if the user is an employee"
  [db company-contract-eid user-eid]
  (->> (d/q '[:find ?cce
              :in $ ?cc ?user
              :where
              [?cc :company-contract/employees ?cce]
              [?cce :company-contract-employee/user ?user]]
            db company-contract-eid user-eid)
       ffirst
       boolean))

(defn available-company-contract-employees
  [db company-contract-eid search]
  (->> (d/q '[:find (pull ?u [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
              :where
              (user-by-name ?u ?search)
              (not-join [?u ?company-contract]
                        [?company-contract :company-contract/employees ?cce]
                        [?cce :company-contract-employee/user ?u])
              :in $ % ?search ?company-contract]
            db user-queries/user-query-rules
            search company-contract-eid)
       (mapv first)))

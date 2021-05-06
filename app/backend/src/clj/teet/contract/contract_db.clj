(ns teet.contract.contract-db
  (:require [datomic.client.api :as d])
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

    [(contract-target-project ?c ?project)
     [?c :thk.contract/targets ?target]
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
    ;In progress - start of work given and that date has passed and deadline > 30
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [(< ?start ?now)]
     (contract-deadline ?c ?act-dl)
     [(teet.util.date/dec-days ?act-dl 30) ?deadline-soon]
     [(< ?now ?deadline-soon)]
     [(ground :thk.contract.status/in-progress) ?s]]
    ;Deadline approaching - Days until deadline < 30 deadline is given
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [(< ?start ?now)]
     [?c :thk.contract/deadline ?deadline]
     (contract-deadline ?c ?act-dl)
     [(teet.util.date/dec-days ?act-dl 30) ?deadline-soon]
     [(> ?deadline ?now)]
     [(> ?now ?deadline-soon)]
     [(ground :thk.contract.status/deadline-approaching) ?s]]
    ;Deadline overdue - Deadline date has passed
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [(< ?start ?now)]
     [(missing? $ ?c :thk.contract/warranty-end-date)]
     (contract-deadline ?c ?act-dl)
     [(< ?act-dl ?now)]
     [(ground :thk.contract.status/deadline-overdue) ?s]]
    ;Warranty - deadline (and extended deadline if given) has passed and warranty date isn't passed
    [(contract-status ?c ?s ?now)
     [?c :thk.contract/start-of-work ?start]
     [(< ?start ?now)]
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
  "Used when after datomic query which returns [contract contract-status]"
  [[contract status]]
  (assoc contract :thk.contract/status status))

(defn get-contract
  [db contract-eid]
  (-> (d/q '[:find (pull ?c [* {:thk.contract/targets [*]}]) ?status
             :where (contract-status ?c ?status ?now)
             :in $ % ?c ?now]
           db
           contract-status-rules
           contract-eid
           (Date.))
      first
      contract-with-status))
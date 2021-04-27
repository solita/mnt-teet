(ns teet.contract.contract-db
  (:require [datomic.client.api :as d]))

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

(defn get-contract
  [db {:thk.contract/keys [procurement-id procurement-part-id]}]
  (if procurement-part-id
    (ffirst (d/q
              '[:find (pull ?c [* {:thk.contract/targets [*]}])
                :where
                [?c :thk.contract/procurement-id ?procurement-id]
                [?c :thk.contract/procurement-part-id ?procurement-part-id]
                :in $ ?procurement-id ?procurement-part-id]
              db procurement-id procurement-part-id))
    (ffirst (d/q
              '[:find (pull ?c [* {:thk.contract/targets [*]}])
                :where
                [?c :thk.contract/procurement-id ?procurement-id]
                [(missing? $ ?c :thk.contract/procurement-part-id)]
                :in $ ?procurement-id]
              db procurement-id))))

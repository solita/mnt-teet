(ns teet.contract.contracts-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.contract.contract-db :as contract-db]
            [teet.contract.contract-model :as contract-model]
            [teet.util.datomic :as du])
  (:import (java.util Date)))

(defmulti contract-search-clause (fn [[attribute _value] _user]
                                   attribute))

(defmethod contract-search-clause :shortcut
  [[_ value] user]
  (case value
    :all-contracts
    {:where '[]}
    :my-contracts
    {:where '[[?c :thk.contract/targets ?target]
              (or-join [?target ?current-user]
                       [?target :activity/manager ?current-user]
                       (and [?a :activity/tasks ?target]
                            [?a :activity/manager ?current-user]))]
     :in {'?current-user (:db/id user)}}
    :unassigned
    {:where '[(contract-target-activity ?c ?activity)
              [(missing? $ ?activity :activity/manager)]]}))

(defmethod contract-search-clause :project-name
  [[_ value] _]
  {:where '[(contract-target-project ?c ?project)
            (project-name-matches? ?project ?project-name-search-value)]
   :in {'?project-name-search-value value}})

(defmethod contract-search-clause :road-number
  [[_ value] _]
  {:where '[(contract-target-project ?c ?project)
            [?project :thk.project/road-nr ?road-number]
            [(teet.util.string/contains-words? ?road-number ?road-number-search-value)]]
   :in {'?road-number-search-value value}})

(defmethod contract-search-clause :contract-name
  [[_ value] _]
  {:where '[[?c :thk.contract/name ?thk-contract-name]
            [(get-else $ ?c :thk.contract/part-name ?thk-contract-name) ?contract-name]
            [(.toLowerCase ^String ?contract-name) ?lower-contract-name]
            [(teet.util.string/contains-words? ?contract-name ?contract-name-search-value)]]
   :in {'?contract-name-search-value (str/lower-case value)}})

(defmethod contract-search-clause :contract-number
  [[_ value] _]
  {:where '[[?c :thk.contract/number ?thk-contract-number]
            [(teet.util.string/contains-words? ?thk-contract-number ?contract-number-search-value)]]
   :in {'?contract-number-search-value value}})

(defmethod contract-search-clause :procurement-number
  [[_ value] _]
  {:where '[[?c :thk.contract/procurement-number ?proc-number]
            [(teet.util.string/contains-words? ?proc-number ?proc-number-search-value)]]
   :in {'?proc-number-search-value value}})

(defmethod contract-search-clause :project-manager
  [[_ {value :db/id}] _]
  {:where '[(contract-target-activity ?c ?activity)
            [?activity :activity/manager ?ac-manager]
            [(= ?ac-manager ?search-user)]]
   :in {'?search-user value}})

(defmethod contract-search-clause :partner-name
  [[_ value] _]
  {:where '[[?cc :company-contract/contract ?c]
            [?cc :company-contract/company ?company]
            [?company :company/name ?c-name]
            [(teet.util.string/contains-words? ?c-name ?search-partner)]]
   :in {'?search-partner value}})

(defmethod contract-search-clause :ta/region
  [[_ value] _]
  {:where '[[?c :ta/region ?region-search-value]]
   :in {'?region-search-value value}})

(defmethod contract-search-clause :contract-type
  [[_ value] _]
  {:where '[[?c :thk.contract/type ?contract-type-search-value]]
   :in {'?contract-type-search-value value}})

(defmethod contract-search-clause :contract-status
  [[_ value] _]
  {:where '[[(= ?calculated-status ?status)]]
   :in {'?status value}})

(defn contract-listing-query
  "takes search params and forms datomic query with the help of contract-search multimethod"
  [db user search-params]
  (let [{:keys [where in]}
        (reduce
          (fn [accumulator attribute]
            (let [{:keys [where in]} (contract-search-clause attribute user)]
              (-> accumulator
                  (update :where concat where)
                  (update :in merge in))))
          {:where '[]
           :in {}}
          search-params)
        arglist (seq in)
        in (into '[$ % ?now] (map first) arglist)
        args (into [db
                    (into contract-db/contract-query-rules
                          contract-db/contract-status-rules)
                    (Date.)]
                   (map second)
                   arglist)]
    (->> (d/q {:query {:find '[(pull ?c [* {:thk.contract/targets [* {:activity/manager [:user/given-name :user/family-name]}]}])
                               ?calculated-status]
                       :where (into '[[?c :thk.contract/procurement-id _]
                                      (contract-status ?c ?calculated-status ?now)]
                                where)
                       :in in}
               :args args})
      (mapv contract-db/contract-with-status)
      (mapv contract-model/db-values->frontend))))

(defquery :contracts/list-contracts
  {:doc "Return a list of contracts matching given search params"
   :context {db :db user :user}
   :args {search-params :search-params}
   :project-id nil
   :authorization {}}
  (->> (contract-listing-query db user (cu/without-empty-vals search-params))
       (sort-by :meta/created-at)
       reverse
       (mapv du/idents->keywords)))

(defquery :contracts/project-related-contracts
  {:doc "Return a list of contracts related to the given project"
   :context {db :db}
   :args {project-id :thk.project/id}
   :project-id project-id
   :authorization {}}
  (->> (contract-db/project-related-contracts db [:thk.project/id project-id])
       (sort-by
         (comp contract-model/contract-status-order :thk.contract/status))
       vec))

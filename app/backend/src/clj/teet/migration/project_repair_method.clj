(ns teet.migration.project-repair-method
  (:require [datomic.client.api :as d]
            [clojure.string :as str]))

;; Fix repair method, previously we took the groupname as repair method
;; and stored short name to integration info.
;;
;; Users actually want the short name shown. So update repair method
;; and integration info to swap them.
(defn fix-repair-method [conn]
  (let [projects (map first
                      (d/q '[:find (pull ?p [:db/id :thk.project/repair-method
                                             :thk.project/integration-info])
                             :where [?p :thk.project/id _]]
                           (d/db conn)))
        parse #(binding [*read-eval* false]
                 (when-not (str/blank? %)
                   (read-string %)))]

    (d/transact
     conn
     {:tx-data (for [{:thk.project/keys [repair-method integration-info]
                      id :db/id} projects
                     :let [old-info (parse integration-info)
                           info (pr-str (assoc old-info
                                               :object/groupname repair-method))
                           repair-method (get old-info :object/groupshortname)]]
                 {:db/id id
                  :thk.project/integration-info info
                  :thk.project/repair-method repair-method})})))

(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.string :as str]))


(defmethod db-api/command! :thk.project/initialize!
  [{conn :conn}
   {:thk.project/keys [id owner project-name]}]
  (let [{:thk.project/keys [estimated-start-date estimated-end-date]}
        (d/pull (d/db conn) [:thk.project/estimated-start-date :thk.project/estimated-end-date]
                [:thk.project/id id])]
    (d/transact
     conn
     {:tx-data [(merge {:thk.project/id id
                        :thk.project/owner [:user/id (:user/id owner)]

                        ;; FIXME: these should be received from THK, now create a design/construction
                        ;; lifecycle that covers the project date range
                        :thk.project/lifecycles
                        (let [start-ms (.getTime estimated-start-date)
                              end-ms (.getTime estimated-end-date)
                              halfway-date (java.util.Date. (+ start-ms (/ (- end-ms start-ms) 2)))]
                          [{:db/id "design-lifecycle"
                            :thk.lifecycle/type [:db/ident :thk.lifecycle-type/design]
                            :thk.lifecycle/estimated-start-date estimated-start-date
                            :thk.lifecycle/estimated-end-date halfway-date}
                           {:db/id "construction-lifecycle"
                            :thk.lifecycle/type [:db/ident :thk.lifecycle-type/construction]
                            :thk.lifecycle/estimated-start-date halfway-date
                            :thk.lifecycle/estimated-end-date estimated-end-date}])}
                       (when-not (str/blank? project-name)
                         {:thk.project/project-name project-name}))]}))
  :ok)

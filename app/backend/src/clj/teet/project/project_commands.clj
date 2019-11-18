(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.string :as str]))


(defmethod db-api/command! :thk.project/initialize! [{conn :conn} {:thk.project/keys [id owner custom-name]}]
  (let [project (d/pull (d/db conn) [:thk.project/estimated-start-date :thk.project/estimated-end-date]
                        [:thk.project/id id])]
    (d/transact
     conn
     {:tx-data [(merge {:thk.project/id id
                        :thk.project/owner [:user/id (:user/id owner)]

                        ;; FIXME: these should be received from THK, now create a design lifecycle
                        ;; that has the same date range as project
                        :thk.project/lifecycles [{:db/id "design-lifecycle"
                                                  :thk.lifecycle/type [:db/ident :thk.lifecycle-type/design]
                                                  :thk.lifecycle/estimated-start-date (:thk.project/estimated-start-date project)
                                                  :thk.lifecycle/estimated-end-date (:thk.project/estimated-end-date project)}]}
                       (when-not (str/blank? custom-name)
                         {:thk.project/custom-name custom-name}))]}))
  :ok)

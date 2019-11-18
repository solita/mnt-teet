(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.string :as str]))


(defmethod db-api/command! :thk.project/initialize! [{conn :conn} {:thk.project/keys [id owner custom-name]}]
  (d/transact conn
              {:tx-data [(merge {:thk.project/id id
                                 :thk.project/owner [:user/id (:user/id owner)]}
                                (when-not (str/blank? custom-name)
                                  {:thk.project/custom-name custom-name}))]})
  :ok)

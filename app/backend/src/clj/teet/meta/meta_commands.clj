(ns teet.meta.meta-commands
  "Generic commands for meta management"
  (:require [teet.db-api.core :refer [defcommand]]
            [teet.meta.meta-query :as meta-query]))

(defcommand :meta/undo-delete
  {:doc "Undo recent entity deletion by the user"
   :context {:keys [db user]}
   :payload {id :db/id}
   :project-id nil
   :authorization {}
   :pre [(meta-query/can-undo-delete? db id user)]
   :transact [[:db/retract id :meta/deleted? true]]})

(ns teet.meta.meta-commands
  "Generic commands for meta management"
  (:require [teet.db-api.core :refer [defcommand]]
            [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]))

(defcommand :meta/undo-delete
  {:doc "Undo recent entity deletion by the user"
   :context {:keys [db user]}
   :payload {id :db/id}
   :project-id nil
   :authorization {}
   :pre [(meta-query/can-undo-delete? db id user)]
   :transact (let [{:meta/keys [deleted? modifier modified-at]}
                   (d/pull db [:meta/deleted? :meta/modifier :meta/modified-at]
                           id)]
               [[:db/retract id :meta/deleted? deleted?]
                [:db/retract id :meta/modifier (:db/id modifier)]
                [:db/retract id :meta/modified-at modified-at]])})

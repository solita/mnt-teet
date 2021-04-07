(ns teet.integration.vektorio.vektorio-queries
  (:require [teet.db-api.core :refer [defquery audit]]
            [teet.integration.vektorio.vektorio-core :as vektorio-core]
            [teet.integration.vektorio.vektorio-client :as vektorio-client]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [teet.user.user-model :as user-model]))

(defn- add-user-to-project!
  [conn user config vektorio-project-id user-id]
  (when
    (empty?
      (d/q '[:find ?pid :where [?u :vektorio/granted-projects ?pid] :in $ ?u ?pid]
           (d/db conn) (user-model/user-ref user) vektorio-project-id))
    (do (vektorio-client/add-user-to-project! conn user config vektorio-project-id user-id)
        (d/transact conn {:tx-data [{:db/id (:db/id user)
                                :vektorio/granted-projects vektorio-project-id}]}))))

(defn- url-for-bim-viewer [conn user vektorio-project-id]
  (let [config (environment/config-value :vektorio)
        user-id (vektorio-core/get-or-create-user! user config)
        _ (add-user-to-project! conn user config vektorio-project-id user-id)
        viewer-url (:viewer-url (:config config))
        response (vektorio-core/instant-login config user-id)
        instantLogin (:instantLogin response)
        viewer-url-with-params (str viewer-url
                                 (if (not-empty vektorio-project-id) (str "&projectId=" vektorio-project-id) "")
                                 "&instantLogin=" instantLogin)]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (str viewer-url-with-params)}}))

(defquery :vektorio/instant-login
  {:doc "Get instant login hash for the default user id to access BIM viewer"
   :context {db :db user :user conn :conn}
   :args {vektorio-project-id :vektorio/project-id project-id :db/id}
   :project-id project-id
   :vektorio-project-id vektorio-project-id
   :authorization {}}
  (url-for-bim-viewer conn user vektorio-project-id))
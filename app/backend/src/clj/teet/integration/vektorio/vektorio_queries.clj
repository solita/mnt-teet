(ns teet.integration.vektorio.vektorio-queries
  (:require [teet.db-api.core :refer [defquery audit]]
            [teet.integration.vektorio.vektorio-core :as vektorio-core]
            [teet.environment :as environment]
            [teet.util.datomic :as du]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.data.json :as json]))


(defn- url-for-bim-viewer [vektorio-project-id]
  (let [config (environment/config-value :vektorio)
        viewer-url (:viewer-url (:config config))
        response (vektorio-core/instant-login config)
        instantLogin (:instantLogin response)
        viewer-url-with-params (str viewer-url
                                 (if (not-empty vektorio-project-id) (str "&projectId=" vektorio-project-id) "")
                                 "&instantLogin=" instantLogin)]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (str viewer-url-with-params)}}))

(defquery :vektorio/instant-login
  {:doc "Get instant login hash for the default user id to access BIM viewer"
   :context {db :db}
   :args {vektorio-project-id :vektorio/project-id project-id :db/id}
   :project-id project-id
   :vektorio-project-id vektorio-project-id
   :authorization {}}
  (let [url-to-bim (url-for-bim-viewer vektorio-project-id)]
    (println "url to bim viewer: " url-to-bim)
    (println "project-id " project-id)
    (println "vektorio-project-id " vektorio-project-id)
    url-to-bim))

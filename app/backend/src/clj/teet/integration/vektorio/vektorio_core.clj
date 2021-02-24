(ns teet.integration.vektorio.vektorio-core
  (:require [teet.integration.vektorio.vektorio-client :as vektorio-client]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]
            [teet.log :as log]
            [teet.integration.integration-s3 :as integration-s3]
            [teet.file.file-storage :as file-storage]
            [clojure.string :as str]
            [teet.file.file-db :as file-db]
            [teet.file.filename-metadata :as filename-metadata]))

(defn create-project-in-vektorio!
  [conn vektor-config project-eid]
  (let [db (d/db conn)
        project (d/pull db [:db/id :thk.project/name :thk.project/project-name] project-eid)
        project-name-for-vektor (or (:thk.project/project-name project)
                                    (:thk.project/name project))
        resp (vektorio-client/create-project! vektor-config {:name project-name-for-vektor})
        vektorio-project-id (str (:id resp))]
    (log/info "Creating project in vektorio for project" project)
    (if-not (some? vektorio-project-id)
      (throw (ex-info "No id for project in Vektorio response"
               {:resp resp
                :error :no-project-id-in-response}))
      (do (d/transact conn {:tx-data [{:db/id (:db/id project)
                                       :vektorio/project-id vektorio-project-id}]})
          vektorio-project-id))))

(defn ensure-project-vektorio-id!
  [conn vektor-config file-eid]
  (let [db (d/db conn)
        project-id (project-db/file-project-id db file-eid)]
    (if-let [project-vektorio-id (:vektorio/project-id (d/pull db [:vektorio/project-id] project-id))]
      project-vektorio-id
      (create-project-in-vektorio! conn vektor-config project-id))))

(defn- vektorio-filepath
  "Returns {activity-code}/{task-code} for given file"
  [db file-id]
  (let [task-activity-code (first (d/q '[:find ?activity-code ?task-code
                                         :in $ ?file
                                         :where
                                         [?task :task/files ?file]
                                         [?task :task/type ?type]
                                         [?activity :activity/tasks ?task]
                                         [?activity :activity/name ?activity-name]
                                         [?activity-name :filename/code ?activity-code]
                                         [?type :filename/code ?task-code]]
                                       db file-id))]
    (str/join "/" task-activity-code)))

(defn- vektorio-filename
  "Returns for example '02_1_Uskuna.dwg'"
  [db file-id]
  (let [file-meta-data (filename-metadata/metadata->vektorio-filename
                         (file-db/file-metadata-by-id db file-id))]
    file-meta-data))

(defn upload-file-to-vektor!
  [conn vektor-config file-id]
  (log/info "Uploading file" file-id "to vektorio")
  (let [db (d/db conn)
        project-vektor-id (ensure-project-vektorio-id! conn vektor-config file-id)
        file-data (d/pull db '[:file/name :db/id :file/s3-key] file-id)
        response (vektorio-client/add-model-to-project! vektor-config {:project-id project-vektor-id
                                                              :model-file (integration-s3/get-object (file-storage/storage-bucket)
                                                                                                     (:file/s3-key file-data))
                                                              :vektorio-filename (vektorio-filename db file-id)
                                                              :vektorio-filepath (vektorio-filepath db file-id)})
        vektorio-model-id (str (:id response))]
    (if-not (some? vektorio-model-id)
      (throw (ex-info "No model id in Vektorio response"
                      {:response response
                       :error :no-model-id-in-response}))
      (d/transact conn {:tx-data [{:db/id (:db/id file-id)
                                   :vektorio/model-id vektorio-model-id}]}))))

(defn instant-login
  [vektorio-config]
  (let [vektorio-user-id (or
                           (:id (vektorio-client/get-user-by-account
                                  vektorio-config
                                  (get-in vektorio-config [:config :api-user])))
                           (:id (vektorio-client/create-user!
                                  vektorio-config
                                  {:account (get-in vektorio-config [:config :api-user])})))]
    (vektorio-client/instant-login vektorio-config {:user-id vektorio-user-id})))

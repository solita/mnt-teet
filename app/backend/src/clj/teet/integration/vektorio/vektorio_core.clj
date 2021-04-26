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

(defn get-or-create-user!
  [user vektorio-config]
  (or
    (:id (vektorio-client/get-user-by-account
           vektorio-config
           (:user/person-id user)))
    (:id (vektorio-client/create-user! vektorio-config
                                       {:account (:user/person-id user)
                                        :name (str (:user/given-name user) " " (:user/family-name user))}))))

(defn create-project-in-vektorio!
  [conn vektor-config project-eid]
  (let [db (d/db conn)
        project (d/pull db [:db/id :thk.project/name :thk.project/project-name :thk.project/id] project-eid)
        project-name (or (:thk.project/project-name project)
                         (:thk.project/name project))
        project-name-for-vektor (str project-name " (THK" (:thk.project/id project) ")")
        resp (vektorio-client/create-project! vektor-config {:name project-name-for-vektor})
        vektorio-project-id (str (:id resp))]
    (log/info "Creating project in vektorio for project" project vektorio-project-id)
    (if-not (some? vektorio-project-id)
      (throw (ex-info "No id for project in Vektorio response"
               {:resp resp
                :error :no-project-id-in-response}))
      (do
        (d/transact conn {:tx-data [{:db/id (:db/id project)
                                     :vektorio/project-id vektorio-project-id}]})
        vektorio-project-id))))

(defn ensure-project-vektorio-id!
  [conn vektor-config file-eid]
  (let [db (d/db conn)
        project-id (project-db/file-project-id db file-eid)]
    (log/info "Ensure the project exists in vektorio for project:" project-id)
    (if-let
      [project-vektorio-id (:vektorio/project-id (d/pull db [:vektorio/project-id] project-id))]
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
                                                                       :model-file (integration-s3/get-object-stream-http
                                                                                     (file-storage/storage-bucket)
                                                                                     (:file/s3-key file-data))
                                                                       :vektorio-filename (vektorio-filename db file-id)
                                                                       :vektorio-filepath (vektorio-filepath db file-id)})
        vektorio-model-id (str (:id response))]
    (log/info "Model id from vektorio response:" vektorio-model-id)
    (if-not (some? vektorio-model-id)
      (throw (ex-info "No model id in Vektorio response"
                      {:response response
                       :error :no-model-id-in-response}))
      (d/transact conn {:tx-data [{:db/id (:db/id file-data)
                                   :vektorio/model-id vektorio-model-id}]}))))

(defn delete-file-from-project! [db vektorio-config project-eid file-eid]
  ;; (du/retractions db file-id [:vektorio/model-id]) ;; won't need if we rely on the file entity being deleted immediately after
  (let [params (merge
                (d/pull db [:vektorio/model-id] file-eid)
                (d/pull db [:vektorio/project-id ] project-eid))
        response (if (not= 2 (count params))
                   (log/info "skipping vektorio delete due to missing model/project ids for file" file-eid)
                   (vektorio-client/delete-model! vektorio-config params))]
    (when response
      (log/info "successfully deleted vektorio model for file" file-eid))
    response))

(defn instant-login
  "Login to VektorIO."
  [vektorio-config vektorio-user-id]
  (vektorio-client/instant-login vektorio-config {:user-id vektorio-user-id}))

(defn update-project-in-vektorio!
  "Updates the project name in Vektor.io should it be changed in TEET"
  [db vektor-config project-id project-name]
  (let [project (d/pull db [:db/id :thk.project/name :thk.project/project-name :thk.project/id :vektorio/project-id] project-id)
        vektor-project-name (str  project-name " (THK" (:thk.project/id project) ")")
        vektor-project-id (:vektorio/project-id project)
        resp (if (some? vektor-project-id)
               (vektorio-client/update-project! vektor-config vektor-project-id vektor-project-name)
               (do (log/info "No Vektor project id found for " project-id)
                   true))]
    resp))
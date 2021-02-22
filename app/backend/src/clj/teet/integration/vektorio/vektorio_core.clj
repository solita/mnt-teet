(ns teet.integration.vektorio.vektorio-core
  (:require [teet.environment :as environment]
            [teet.integration.vektorio.vektorio-client :as vektorio-client]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]
            [teet.integration.integration-s3 :as integration-s3]
            [teet.file.file-storage :as file-storage]))

;; TODO rename to vektorio-core

(def vektor-config (environment/config-value :vektorio))

(defn create-project-in-vektorio
  [conn project-eid]
  (let [db (d/db conn)
        project (d/pull db [:db/id :thk.project/name :thk.project/project-name] project-eid)
        project-name-for-vektor (or (:thk.project/project-name project)
                                    (:thk.project/name project))
        resp (vektorio-client/create-project! vektor-config {:name project-name-for-vektor})
        vektorio-project-id (str (:id resp))]
    (if-not (some? vektorio-project-id)
      (throw (ex-info "No vektorio id in message"
               {:resp resp
                :error :no-project-id-in-response}))
      (do (d/transact conn {:tx-data {:db/id (:db/id project)
                                      :vektorio/project-id vektorio-project-id}})
          vektorio-project-id))))

(defn ensure-project-vektorio-id
  [conn file-eid]
  (let [db (d/db conn)
        project-id (project-db/file-project-id db file-eid)]
    (if-let [project-vektorio-id (:vektorio/project-id (d/pull db [:vektorio/project-id] project-id))]
      project-vektorio-id
      (create-project-in-vektorio conn project-id))))

;; TODO should this fn check the projects vektorio-id status?
;; TODO FETCH TASK FILENAMECODE and ACTIVITY FILENAME CODE to use as path variable in vektor "/ES/TL/" for example
(defn upload-file-to-vektor
  [conn vektor-config file-id]
  (let [db (d/db conn)
        project-vektor-id (ensure-project-vektorio-id db file-id)
        file-data (d/pull db '[:file/name :file/s3-key] file-id)]
    (vektorio-client/add-model-to-project! vektor-config {:project-id project-vektor-id
                                                          :model-file (integration-s3/get-object (file-storage/storage-bucket)
                                                                                                 (:file/s3-key file-data))
                                                          :vektorio-filename (:file/name file-data)
                                                          :vektorio-filepath ;; ACTIVITY/file-key
                                                          "GET THIS"})

    ;; TODO save model id to file
    ))

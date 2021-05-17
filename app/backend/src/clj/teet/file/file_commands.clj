(ns teet.file.file-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.file.file-storage :as file-storage]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta deletion-tx] :as meta-model]
            [teet.file.file-model :as file-model]
            [teet.project.project-db :as project-db]
            [teet.file.file-db :as file-db]
            [teet.log :as log]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            teet.file.file-tx
            [teet.util.collection :as cu]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.project.task-model :as task-model]
            [teet.environment :as environment]
            [teet.integration.vektorio.vektorio-core :as vektorio]
            [teet.file.file-export :as file-export]
            [teet.integration.integration-s3 :as integration-s3]
            [teet.integration.integration-email :as integration-email]
            [teet.localization :refer [tr tr-enum with-language]]))

(defn- new-file-key [{name :file/name}]
  (str (java.util.UUID/randomUUID) "-" name))

(defn latest-file-version-id
  [db file-id]
  (if-let [parent (get-in (du/entity db file-id) [:file/_previous-version 0 :db/id])]
    (recur db parent)
    file-id))

(defn- find-previous-version
  ([db task-id previous-version]
   (find-previous-version db task-id previous-version []))
  ([db task-id previous-version optional-query-keys]
   (let [query-keys (into [:db/id :file/version :file/sequence-number
                           {:file/part [:db/id :file.part/number :file.part/name]}]
                          optional-query-keys)]
     (if-let [old-file (ffirst (d/q '[:find (pull ?f attrs)
                                      :in $ ?f ?t attrs
                                      :where
                                      [?t :task/files ?f]]
                                    db
                                    (latest-file-version-id db previous-version)
                                    task-id
                                    query-keys))]
       old-file
       (db-api/bad-request! "Can't find previous version")))))

(def file-keys [:file/name :file/size :file/document-group :file/sequence-number :file/part])

(defcommand :file/upload-attachment
  {:doc "Upload attachment file and optionally attach it to entity."
   :context {:keys [conn user db]}
   ;; TODO: Pass project id to check project authz
   :payload {:keys [file project-id attach-to]}
   :project-id project-id
   :authorization {:project/upload-comment-attachment {}}
   :pre [^{:error :comment-attachment-image-only}
         (or attach-to
             (file-model/image? file))

         ^{:error :attach-pre-check-failed}
         (or (nil? attach-to)
             (file-db/attach-to db user file attach-to))]}
  (log/debug "upload-attachment: got project-id" project-id)

  (let [{attach-to-eid :eid
         wrap-tx :wrap-tx} (when attach-to
                             (file-db/attach-to db user file attach-to))
        wrap-tx (or wrap-tx identity)
        key (new-file-key file)
        res (tx (wrap-tx
                 [(merge (select-keys file file-keys)
                         {:db/id "new-file"
                          :file/s3-key key}
                         (when attach-to-eid
                           {:file/attached-to attach-to-eid})
                         (creation-meta user))]))
        file-id (get-in res [:tempids "new-file"])]

    {:url (file-storage/upload-url key)
     :file (d/pull (:db-after res) '[*] file-id)}))

(defn maybe-vektorio-delete! [db file-eid]
  (let [vektorio-enabled? (environment/feature-enabled? :vektorio)
        vektorio-config (environment/config-value :vektorio)
        project-eid (try
                      (project-db/file-project-id db file-eid)
                      (catch clojure.lang.ExceptionInfo e
                        (log/info "didn't find a project-id for file being deleted so skipping vektorio delete, file id:" file-eid)))]
    (when project-eid
      (log/debug "delete corresponding model from vektorio? " (some? vektorio-enabled?))
      (when vektorio-enabled?
        (vektorio/delete-file-from-project! db vektorio-config project-eid file-eid)))))

(defcommand :file/delete-attachment
  {:doc "Delete an attachment"
   :context {:keys [user db]}
   :payload {:keys [file-id attached-to]}
   :project-id nil
   :authorization {}
   ;; this command has modal behaviour depending on whether attached-to tuple is supplied, or nil.
   ;; if it is supplied, permission check and delete-attachment call go to the multimethod that
   ;; will do a permission check depending on the attachment type.
   :pre [(or attached-to
             (file-db/own-file? db user file-id))]
   :transact (let [file-delete-tx (if attached-to
                                    (file-db/delete-attachment db user file-id attached-to)
                                    [(deletion-tx user file-id)])]
               file-delete-tx)})

(defcommand :file/upload-complete
  {:doc "Mark file upload as complete"
   :context {:keys [conn user db]}
   :payload {id :db/id}
   :pre [(file-db/own-file? db user id)]
   ;; No need to check extra authorization again, as we check pre condition that this
   ;; is the user's own uploaded file
   :project-id nil
   :authorization {}}
  (let [previous-version (:file/previous-version (du/entity db id))
        previous-links (when previous-version
                         (map first (d/q '[:find ?l
                                           :where [?l :link/to ?id]
                                           :in $ ?id]
                                         db (:db/id previous-version))))
        file-id (when previous-version
                  (:file/id previous-version))
        res (tx [{:db/id id
                  :file/upload-complete? true
                  :file/id (or file-id
                               (java.util.UUID/randomUUID))}]
                (when file-id
                  ;; If moving :file/id from previous version, retract it from
                  ;; previous file entity
                  [[:db/retract (:db/id previous-version) :file/id file-id]])
                ;; Move links to point to the new version the file
                (when previous-links
                  (for [link previous-links]
                    {:db/id link
                     :link/to id})))]
    (d/pull (:db-after res) '[:db/id :file/id] id)))

(defn file-belong-to-task?
  [db task-id file-id]
  (let [file-entity (du/entity db file-id)]
    (= task-id (get-in file-entity [:task/_files 0 :db/id]))))

(defcommand :file/replace
  {:doc "replace existing file with a new version. Save file size"
   :context {:keys [conn user db]}
   :payload {:keys [task-id file previous-version-id]}
   :project-id (project-db/task-project-id db task-id)
   :pre [^{:error :invalid-previous-file}
         (file-belong-to-task? db task-id previous-version-id)
         ^{:error :replaced-file-not-latest}
         (= (latest-file-version-id db previous-version-id) previous-version-id)]
   :authorization {:document/upload-document {:db/id task-id
                                              :link :task/assignee}}}
  (let [{description :description} (filename-metadata/name->description-and-extension (:file/name (du/entity db previous-version-id)))]
    (if-let [error (file-model/validate-file (merge file
                                                    {:file/description description}))]
      (throw (ex-info "invalid file"
                      error))
      (let [old-file (find-previous-version db task-id previous-version-id
                                            [:file/name :file/document-group])
            version (-> old-file :file/version inc)
            ;; If filetype changes we need to change the extension in the filename, but keep the original name
            file-name-with-extension (str
                                       (:description
                                         (filename-metadata/name->description-and-extension
                                           (:file/name old-file)))
                                       "."
                                       (:extension
                                         (filename-metadata/name->description-and-extension
                                           (:file/name file))))

            key (new-file-key file)
            res (tx [(list 'teet.file.file-tx/upload-file-to-task
                           {:db/id task-id
                            :task/files
                            [(cu/without-nils
                               (merge
                                 old-file
                                 (select-keys file [:file/size])
                                 {:db/id "new-file"
                                  :file/s3-key key
                                  :file/original-name (:file/name file)
                                  :file/version version
                                  :file/previous-version (:db/id old-file)
                                  :file/status :file.status/draft
                                  :file/name file-name-with-extension}
                                 (creation-meta user)))]})])
            file-id (get-in res [:tempids "new-file"])]
        (try
          {:url (file-storage/upload-url key)
           :file (d/pull (:db-after res) '[*] file-id)}
          (catch Exception e
            (log/warn e "Unable to create S3 presigned URL")
            (throw e)))))))

(defcommand :file/upload
  {:doc "Upload new file to task."
   :context {:keys [conn user db]}
   :payload {:keys [task-id file previous-version-id] :as p}
   :project-id (project-db/task-project-id db task-id)
   :pre [^{:error :invalid-previous-file}
         (or (nil? previous-version-id)
             (file-belong-to-task? db task-id previous-version-id))
         ^{:error :invalid-task-status}
         (task-model/can-submit? (d/pull db [:task/status] task-id))]
   :authorization {:document/upload-document {:db/id task-id
                                              :link :task/assignee}}}
  (if-let [error (file-model/validate-file file)]
    (throw (ex-info "invalid file"
                    error))
    (let [old-file (when previous-version-id
                     (find-previous-version db task-id previous-version-id))
          version (or (some-> old-file :file/version inc) 1)

          key (new-file-key file)
          tx-data [(list 'teet.file.file-tx/upload-file-to-task user
                         {:db/id (or task-id "new-task")
                          :task/files [(cu/without-nils
                                         (merge (select-keys file file-keys)
                                                {:db/id "new-file"
                                                 :file/s3-key key
                                                 :file/status :file.status/draft
                                                 :file/version version
                                                 :file/original-name (:file/name file)}
                                                (when (:file/description file)
                                                  {:file/name (str (:file/description file) "." (:file/extension file))})
                                                (when old-file
                                                  {:file/previous-version (:db/id old-file)})

                                                ;; Replacement version is uploaded to the same part
                                                (when-let [old-part (:file/part old-file)]
                                                  {:file/part old-part})
                                                (when-let [old-seq-number (:file/sequence-number old-file)]
                                                  {:file/sequence-number old-seq-number})
                                                (creation-meta user)))]})]
          res (tx tx-data)
          t-id (or task-id (get-in res [:tempids "new-task"]))
          file-id (get-in res [:tempids "new-file"])]
      (try
        {:url (file-storage/upload-url key)
         :task-id t-id
         :file (d/pull (:db-after res) '[*] file-id)}
        (catch Exception e
          (log/warn e "Unable to create S3 presigned URL")
          (throw e))))))

(defcommand :file/delete
  {:doc "Delete file and all its versions."
   :context {:keys [user db]}
   :payload {:keys [file-id]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/delete-document {:db/id file-id}}
   :transact (let [file-delete-tx (vec
                              (for [version-id (file-db/file-versions db file-id)]
                                (deletion-tx user version-id)))]
               (maybe-vektorio-delete! db file-id)
               file-delete-tx)})

(defcommand :file/seen
  {:doc "Mark that I have seen this file"
   :context {:keys [user db]}
   :payload {:keys [file-id]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}
   :transact [(let [user-id (->> user user-model/user-ref (du/entity db) :db/id)]
                {:db/id "file-seen"
                 :file-seen/file file-id
                 :file-seen/user user-id
                 :file-seen/file+user [file-id user-id]
                 :file-seen/seen-at (java.util.Date.)})]})

(defn maybe-update-vektorio-model-name! [conn db id]
  (let [vektorio-enabled? (environment/feature-enabled? :vektorio)
        vektorio-config (environment/config-value :vektorio)]
    (if vektorio-enabled?
      (vektorio/update-model-in-vektorio! conn db vektorio-config id)
      true)))

(defcommand :file/modify
  {:doc "Modify task file info: part, group, sequence and name."
   :context {:keys [conn user db]}
   :payload {id :db/id :as file}
   :project-id (project-db/file-project-id db id)
   :authorization {:document/overwrite-document {:db/id id}}
   :pre [^{:error :description-too-long}
         (-> file
             :file/name
             filename-metadata/name->description-and-extension
             :description
             file-model/valid-description-length?)
         ^{:error :invalid-chars-in-description}
         (-> file
             :file/name
             filename-metadata/name->description-and-extension
             :description
             file-model/valid-chars-in-description?)]}
   (let [{db-after :db-after} (tx [(list 'teet.file.file-tx/modify-file
                                         (merge file
                                                (meta-model/modification-meta user)))])]
            (try (do (maybe-update-vektorio-model-name! conn db-after id))
                 (catch Exception e
                   (if
                     (some? (get-in (ex-data e) [:vektorio-response :reason-phrase]))
                     (db-api/fail!
                       {:status 400
                        :msg (str "Vektor.io error:" (get-in (ex-data e) [:vektorio-response :reason-phrase]))
                        :error :vektorio-request-failed})
                     (throw e))))))

(defn- export-zip [{:keys [db user export-fn export-bucket email-subject-message] :as opts}]
  (if-let [email (:user/email (du/entity db (user-model/user-ref user)))]
    (future
      (try
        (let [{:keys [filename input-stream]} (export-fn)
              s3-key (str (java.util.UUID/randomUUID))
              _response (integration-s3/put-object export-bucket
                                                   s3-key
                                                   input-stream)
              download-url (integration-s3/presigned-url {:content-disposition (str "attachment; filename=" filename)
                                                          :expiration-seconds (* 24 60 60)}
                                                         "GET" export-bucket s3-key)]
          (integration-email/send-email!
           {:to email
            :subject email-subject-message
            :body [{:type "text/plain; charset=utf-8"
                    :content (tr [:file :export-files-zip :email-body]
                                 {:link (str "\n" download-url "\n")})}]}))
        (catch Throwable t
          (log/error t "Error exporting zip" opts))))
    (db-api/fail! {:error :user-has-no-email})))

(defcommand :file/export-task
  {:doc "Export task files as zip."
   :context {:keys [user db]}
   :payload {:keys [task-id language]}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:project/read-info {}}
   :config {export-bucket [:document-storage :export-bucket-name]}
   :pre [^{:error :configuration-missing}
         (some? export-bucket)]}
  (with-language language
    (export-zip {:db db
                 :user user
                 :export-bucket export-bucket
                 :export-fn #(file-export/task-zip db task-id)
                 :email-subject-message (tr [:file :export-files-zip :task-email-subject]
                                            {:task (tr-enum (:task/type (du/entity db task-id)))})}))
  {:success? true})

(defcommand :file/export-activity
  {:doc "Export activity files as zip."
   :context {:keys [user db]}
   :payload {:keys [activity-id language]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:project/read-info {}}
   :config {export-bucket [:document-storage :export-bucket-name]}
   :pre [^{:error :configuration-missing}
         (some? export-bucket)]}
  (with-language language
    (export-zip {:db db
                 :user user
                 :export-bucket export-bucket
                 :export-fn #(file-export/activity-zip db activity-id)
                 :email-subject-message (tr [:file :export-files-zip :activity-email-subject]
                                            {:activity (tr-enum (:activity/name (du/entity db activity-id)))})}))
  {:success? true})

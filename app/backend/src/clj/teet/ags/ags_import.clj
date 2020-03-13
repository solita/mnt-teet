(ns teet.ags.ags-import
  "Import features from an AGS file in S3"
  (:require [teet.ags.ags-parser :as ags-parser]
            [teet.ags.ags-features :as ags-features]
            [teet.integration.integration-context :refer [defstep]]
            [teet.integration.integration-s3 :as integration-s3]
            [datomic.client.api :as d]
            [teet.project.project-model :as project-model]
            [teet.document.document-storage :as document-storage]
            [teet.gis.entity-features :as entity-features]))

(defn ags->geojson [input]
  (ags-parser/parse input))

(defstep fetch-project-ags-files
  {:doc "Find uploaded .ags files in project. Takes in connection (:conn) and project entity id (:project)"
   :in {conn {:spec some?
              :default-path [:conn]
              :path-kw :conn}
        project-id {:spec ::project-model/id
                    :default-path [:project]
                    :path-kw :project}}
   :out {:spec some? ;; FIXME: provide specs
         :default-path [:ags-files]}}
  (mapv first
        (d/q
         '[:find (pull ?file [:db/id :file/name :file/size :file/type])
           :where
           [?project-id :thk.project/lifecycles ?lifecycle]
           [?lifecycle :thk.lifecycle/activities ?activity]
           [?activity :activity/tasks ?task]
           [?task :task/documents ?doc]
           [?doc :document/files ?file]
           [?file :file/name ?file-name]
           [(.endsWith ^String ?file-name ".ags")]

           :in $ ?project-id]

         (d/db conn)
         project-id)))

(defstep load-ags-files
  {:doc "Load all AGS file data from S3."
   :in {files {:spec coll?
               :default-path [:ags-files]
               :path-kw :ags-files}}
   :out {:spec coll?
         :default-path [:ags-files]}}
  (mapv (fn [file]
          (dissoc
           (->> {:s3 (document-storage/document-s3-ref file)}
                integration-s3/load-file-from-s3
                :file
                ags-parser/parse
                doall
                (assoc file :contents))
           :file))
        files))

(defstep prepare-features
  {:doc "Prepare features from AGS data"
   :in {files {:spec coll?
               :default-path [:ags-files]
               :path-kw :ags-files}}
   :out {:spec coll?
         :default-path [:ags-files]}}
  (mapv (fn [file]
          (->> file
               :contents
               ags-features/ags-features
               (assoc file :features)))
        files))

(defstep upsert-features
  {:doc "Upsert entity features to PostgREST"
   :in {files {:spec coll?
               :default-path [:ags-files]
               :path-kw :ags-files}
        api-url {:spec string?
                 :default-path [:api-url]
                 :path-kw :api-url}
        api-secret {:spec string?
                    :default-path [:api-secret]
                    :path-kw :api-secret}}
   :out {:spec some?
         :default-path [:upsert-status]}}
  (doall
   (for [{entity-id :db/id
          features :features} files]
     (entity-features/upsert-entity-features! {:api-url api-url
                                              :api-secret api-secret}
                                              entity-id
                                              features))))

(defn import-project-ags-files [ctx]
  (-> ctx
      fetch-project-ags-files
      load-ags-files
      prepare-features
      upsert-features))

(ns teet.ags.ags-import
  "Import features from an AGS file in S3"
  (:require [teet.ags.ags-parser :as ags-parser]
            [teet.integration.integration-context :refer [ctx-> defstep]]
            [teet.integration.integration-s3 :as integration-s3]
            [datomic.client.api :as d]))

(defn ags->geojson [input]
  (ags-parser/parse input))


(defstep fetch-project-ags-files
  {:doc "Find uploaded .ags files in project. Takes in connection (:conn) and project entity id (:project)"
   :in {conn {:spec some?
              :default-path [:conn]
              :path-kw :conn}
        project-id {:spec integer?
                    :default-path [:project]
                    :path-kw :project}}
   :out {:spec some? ;; FIXME: provide specs
         :default-path [:ags-files]}}
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
   project-id))

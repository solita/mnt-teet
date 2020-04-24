(ns teet.file.file-db
  "File queries"
  (:require [datomic.client.api :as d]))

(defn own-file? [db user file-id]
  (boolean
   (ffirst
    (d/q '[:find ?f
           :where
           [?f :file/name _]
           [?f :meta/creator ?user]
           :in $ ?user ?f]
         db [:user/id (:user/id user)] file-id))))

(defn file-is-attached-to-comment? [db file-id comment-id]
  (boolean
   (ffirst
    (d/q '[:find ?f
           :where [?c :comment/files ?f]
           :in $ ?f ?c]
         db file-id comment-id))))

(defn file-metadata [db file-id]
  (let [[project activity task file]
        (first
         (d/q '[:find
                (pull ?project [:thk.project/id])
                (pull ?activity [{:activity/name [:filename/code]}])
                (pull ?task [{:task/type [:filename/code]}])
                (pull ?file [:file/name :file/group-number])
                :where
                [?task :task/files ?file]
                [?activity :activity/tasks ?task]
                [?lifecycle :thk.lifecycle/activities ?activity]
                [?project :thk.project/lifecycles ?lifecycle]
                :in
                $ ?file]
              db file-id))]
    {:thk.project/id (:thk.project/id project)
     :activity (get-in activity [:activity/name :filename/code])
     :task (get-in task [:task/type :filename/code])
     :group (:file/group-number file)
     :name (:file/name file)}))

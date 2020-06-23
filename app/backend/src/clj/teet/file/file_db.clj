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

(defn file-listing
  "Fetch file information suitable for file listing. Returns all file attributes
  and owner info. Returns only the latest version of each file with previous versions
  as :versions key."
  [db file-ids]
  (let [files (mapv first
                    (d/q '[:find (pull ?f [*
                                           {:file/previous-version [:db/id]}
                                           {:meta/creator [:user/id :user/family-name :user/given-name]}])
                           :in $ [?f ...]] db file-ids))
        ;; Group files to first versions and next versions
        {next-versions true
         first-versions false}
        (group-by (comp boolean :file/previous-version) files)

        ;; Find next version for given file
        next-version (fn [file]
                       (some #(when (= (:db/id file)
                                       (get-in % [:file/previous-version :db/id])) %)
                             next-versions))]
    (vec
     (for [f first-versions
           :let [versions (filter (complement :meta/deleted?)
                                  (reverse
                                   (take-while some? (iterate next-version f))))
                 [latest-version & previous-versions] versions]
           :when latest-version]
       (assoc latest-version
              :versions previous-versions)))))

(defn files-by-project-and-pos-number [db project-id pos-number]
  (file-listing
   db
   (mapv first
         (d/q '[:find ?f
                :where
                ;; File has this position number
                [?f :file/pos-number ?pos]

                ;; File belongs to the project
                [?task :task/files ?f]
                [?act :activity/tasks ?task]
                [?lc :thk.lifecycle/activities ?act]
                [?project :thk.project/lifecycles ?lc]

                :in $ ?project ?pos]
              db project-id pos-number))))

(defn file-count-by-project-and-pos-number
  [db project-id pos-number]
  ;; Could be improved with some distinct query magic to query only the count
  (count (files-by-project-and-pos-number db project-id pos-number)))


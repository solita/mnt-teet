(ns teet.file.file-db
  "File queries"
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]))

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

(defn files-seen-at
  "Return mapping of file-id to timestamp when the given user has seen a given
  file. If the user has not seen a file, the file-id will not have a mapping."
  [db user file-ids]
  (into {}
        (d/q '[:find ?f ?at
               :where
               [?fs :file-seen/file ?f]
               [?fs :file-seen/user ?user]
               [?fs :file-seen/seen-at ?at]
               :in $ ?user [?f ...]]
             db (user-model/user-ref user) file-ids)))


(defn files-comment-counts
  "Return mapping of file-id to map containing the amount of comments
  the file has (both total comments and new).

  Takes in a database, a mapping of when user has seen a file (files-seen-at)
  and file-ids."
  [db seen-at-by-file file-ids]
  (->> (d/q '[:find ?f ?ts
              :keys :file-id :comment-ts
              :where
              [?f :file/comments ?c]
              [(missing? $ ?c :meta/deleted?)]
              [?c :comment/timestamp ?ts]
              :in $ [?f ...]]
            db
            file-ids)
       (group-by :file-id)
       (cu/map-vals
        (fn [comments]
          (cu/count-by (fn [{f :file-id ts :comment-ts}]
                         (let [seen (seen-at-by-file f)]
                           (if (or (nil? seen)
                                   (.before seen ts))
                             :file/comments-new-count
                             :file/comments-old-count)))
                       comments)))))

(defn file-listing
  "Fetch file information suitable for file listing. Returns all file attributes
  and owner info. Returns only the latest version of each file with previous versions
  as :versions key.

  Includes the timestamp user has seen the file (if any) as :file-seen/seen-at."
  [db user file-ids]
  (let [;; File seen statuses
        seen-at-by-file (files-seen-at db user file-ids)

        ;; Get comment counts for files
        ;; FIXME: take all/internal visibility into account!!!
        comment-counts-by-file
        (files-comment-counts db seen-at-by-file file-ids)

        files
        (mapv
         (comp #(merge %
                       (comment-counts-by-file (:db/id %))
                       {:file-seen/seen-at (seen-at-by-file (:db/id %))})
               first)
         (d/q '[:find (pull ?f [:db/id :file/name :file/version :file/size :file/type :file/status
                                {:file/previous-version [:db/id]}
                                {:meta/creator [:user/id :user/family-name :user/given-name]}])
                :where [?f :file/upload-complete? true]
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

(defn files-by-project-and-pos-number [db user project-id pos-number]
  (file-listing
   db user
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
  [db user project-id pos-number]
  ;; Could be improved with some distinct query magic to query only the count
  (count (files-by-project-and-pos-number db user project-id pos-number)))

(ns teet.file.file-db
  "File queries"
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.comment.comment-db :as comment-db]
            [teet.log :as log]))

(defn- valid-attach-definition? [attach]
  (and (vector? attach)
       (= 2 (count attach))
       (keyword? (first attach))))

(defn check-attach-definition [attach]
  (if-not (valid-attach-definition? attach)
    (throw (ex-info "Invalid attach definition"
                    {:invalid-attach-definition attach}))
    attach))

(defmulti attach-to
  "Check preconditions and permissions for attaching file to
  entity of given type and id for the user.

  Returna a map containing :eid to attach to if attaching is allowed, nil if not.
  May also throw an exception with :error key in the exception.

  Return map may also contain :wrap-tx function which will be used to process
  the tx-data vector before invoking the transaction.

  Default behaviour is to disallow."
  (fn [_db _user _file attach]
    (first (check-attach-definition attach))))

(defmulti allow-download-attachments?
  "Check preconditions and permissions for downloading files attached
  to given entity.

  Default behaviour is to disallow."
  (fn [_db _user attach]
    (first (check-attach-definition attach))))

(defmulti delete-attachment
  "Create transaction data for deleting a file attachment.
  Must check preconditions and permissions for deleting the attached file
  and throw exception with :error key in the data on failure.

  Default behaviour is to disallow and throw an exception."
  (fn [_db _user _file-id attach]
    (first (check-attach-definition attach))))

(defmethod attach-to :default [_db user _file [entity-type entity-id]]
  (log/info "Disallow attaching file to " entity-type " with id " entity-id
            " for user " user)
  nil)

(defmethod allow-download-attachments? :default
  [_db user [entity-type entity-id]]
  (log/info "Disallow downloading attachments to " entity-type
            " with id " entity-id " for user " user))

(defmethod delete-attachment :default
  [_db user file-id [entity-type entity-id]]
  (log/info "Disallow deleting attachment " file-id " to "
            entity-type " with id " entity-id " for user " user)
  (throw (ex-info "Delete attachment not allowed"
                  {:error :not-allowed})))

(defn file-is-attached-to? [db file-id attach]
  (and (valid-attach-definition? attach)
       (boolean
        (seq
         (d/q '[:find ?eid
                :where [?f :file/attached-to ?eid]
                :in $ ?f ?eid]
              db file-id (second attach))))))

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


(defn file-ids-with-comment-counts
  [db file-ids user]
  (reduce
    (fn [val id]
      (merge val {id (comment-db/comment-count-of-entity-by-status db user id :file)}))
    {}
    file-ids))

(defn file-listing
  "Fetch file information suitable for file listing. Returns all file attributes
  and owner info. Returns only the latest version of each file with previous versions
  as :versions key.

  Includes the timestamp user has seen the file (if any) as :file-seen/seen-at."
  [db user file-ids]
  ;; TODO separate comment-count per file and file listing
  (let [;; File seen statuses
        seen-at-by-file (files-seen-at db user file-ids)

        ;; Get comment counts for files
        comment-counts-by-file
        (file-ids-with-comment-counts db file-ids user)

        files
        (mapv
         (comp #(merge %
                       (comment-counts-by-file (:db/id %))
                       {:file-seen/seen-at (seen-at-by-file (:db/id %))})
               first)
         (d/q '[:find (pull ?f [:db/id :file/name :meta/deleted? :file/version :file/size :file/status
                                {:file/previous-version [:db/id]}
                                :meta/created-at
                                {:meta/creator [:user/id :user/family-name :user/given-name]}])
                :where
                [?f :file/upload-complete? true]
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

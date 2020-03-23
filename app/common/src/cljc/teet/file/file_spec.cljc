(ns teet.file.file-spec
  "Specs for document data"
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [teet.file.file-model :as file-model]))


(s/def ::task-id integer?)
(s/def ::file-id integer?)

(s/def :file/upload (s/keys :req [:file/name :file/type :file/size :thk/id]
                                :opt-un [::task-id]))
(s/def :file/download-file (s/keys :req-un [::file-id]))
(s/def :file/status keyword?)
(s/def :file/name (s/and string? (complement str/blank?)))
(s/def :file/type string?)
(s/def :file/size integer?)
(s/def :file/description (s/and string? (complement str/blank?)))
(s/def :file/category keyword?)
(s/def :file/sub-category keyword?)
(s/def :file/author (s/keys :req [:user/id]))
(s/def :file/new-file-form (s/keys :req [:file/category
                                         :file/sub-category
                                         :file/name
                                         :file/status
                                         :file/description
                                         :file/status
                                         :file/author]))

#?(:cljs
   (do
     (s/def :file/files (s/coll-of :file/file-object))
     (s/def :file/file-object
       (s/and
         #(instance? js/File %)
         #(s/valid? :file/file (file-model/file-info %))))))

(s/def :activity/name keyword?)
(s/def :activity/status keyword?)
(s/def :activity/estimated-end-date inst?)
(s/def :activity/estimated-start-date inst?)
(s/def :activity/estimated-date-range (s/coll-of inst? :count 2))
(s/def :file/new-activity-form (s/keys :req [(or :db/id :activity/name)
                                                 :activity/estimated-start-date
                                                 :activity/estimated-end-date]))

(s/def :comment/comment (s/and string? (complement str/blank?)))
(s/def :file/new-comment-form (s/keys :req [:comment/comment]))

(s/def :file/file-id integer?)
(s/def :file/comment (s/keys :req-un [:comment/comment :file/file-id]))

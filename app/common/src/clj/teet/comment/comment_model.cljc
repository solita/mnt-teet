(ns teet.comment.comment-model)

(defn comments-attribute-for-entity-type [entity-type]
  (case entity-type
    :task :task/comments
    :file :file/comments))

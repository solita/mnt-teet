(ns teet.comment.comment-model)

(def entity-comment-attribute
  {:task :task/comments
   :file :file/comments})

(defn comments-attribute-for-entity-type [entity-type]
  (or (entity-comment-attribute entity-type)
      (throw (ex-info "Don't know what the comment attribute is for entity type"
                      {:entity-type entity-type}))))

(def tracked-statuses #{:comment.status/unresolved :comment.status/resolved})

(defn tracked? [comment]
  (boolean (tracked-statuses (-> comment :comment/status :db/ident))))

(def untracked? (complement tracked?))

(defn unresolved? [comment]
  (= :comment.status/unresolved
     (-> comment :comment/status :db/ident)))

(defn resolved? [comment]
  (= :comment.status/resolved
     (-> comment :comment/status :db/ident)))

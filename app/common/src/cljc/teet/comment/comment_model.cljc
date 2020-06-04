(ns teet.comment.comment-model)

(def user-mention-pattern
  "Regex pattern for user mentions, like @[name](id), eg. @[Carla Consultant](123456)"
  #"(@\[[^\]]+\]\(\d+\))")

(def user-mention-name-pattern
  "Regex pattern for extracting user name from mention."
  #"^@\[([^\]]+)\]\(\d+\)$")

(def user-mention-id-pattern
  "Regex pattern for extracting user id from mention."
  #"^@\[[^\]]+\]\((\d+)\)$")

(def type->comments-attribute
  {:task :task/comments
   :file :file/comments
   :estate-comments :estate-comments/comments
   :owner-comments :owner-comments/comments})

(defn comments-attribute-for-entity-type [entity-type]
  (or (type->comments-attribute entity-type)
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

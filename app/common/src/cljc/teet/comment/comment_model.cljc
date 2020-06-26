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
   :owner-comments :owner-comments/comments
   :unit-comments :unit-comments/comments})

(def ^:private task-project-id-path
  [:activity/_tasks 0 :thk.lifecycle/_activities 0 :thk.project/_lifecycles 0 :db/id])

(def ^{:doc "All paths from comment that lead to project id"}
  comment-project-paths
  [(into [:task/_comments 0] task-project-id-path) ; task project id
   (into [:file/_comments 0 :task/_files 0] task-project-id-path) ; file project id
   [:unit-comments/_comments 0 :unit-comments/project :db/id] ; unit comments
   [:owner-comments/_comments 0 :owner-comments/project :db/id] ; owner comments
   [:estate-comments/_comments 0 :estate-comments/project :db/id] ; estate comments
   ])



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

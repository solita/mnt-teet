(ns teet.link.link-db
  "Common db queries for fetching links between entities"
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]))

(defmulti link-info
  "Fetch link display info by type and target entity id. Should return map
  of the info needed to display the link in human readable format."
  (fn [_db type _id] type))

(defn expand-links
  "Expand all links in the given form to their display representations.
  Each link is added with a :link/info which is the fetched display
  representation."
  [db form]
  (walk/prewalk
   (fn [x]
     (if (and (map? x)
              (contains? x :link/type)
              (contains? x :link/to))
       (assoc x :link/info (link-info db (:link/type x) (:db/id (:link/to x))))
       x)) form))

(defmethod link-info :task [db _ task-id]
  (d/pull db [:task/type
              {:task/assignee [:user/given-name :user/family-name]}
              :task/estimated-end-date
              {:activity/_tasks [:activity/name]}]
          task-id))

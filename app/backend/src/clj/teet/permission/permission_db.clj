(ns teet.permission.permission-db
  "Datomic queries related to project user permissions"
  (:require [datomic.client.api :as d])
  (:import (java.util Date)))

(defn valid-project-permissions
  "User and project are either db/ids or lookup refs."
  ([db project]
   (valid-project-permissions db project (Date.)))
  ([db project time]
   (mapv first
         (d/q '[:find (pull ?perm [:permission/role :db/id :meta/created-at
                                   {:user/_permissions [:db/id :user/person-id :user/family-name :user/given-name :user/email]}])
                :in $ ?project ?time
                :where
                [?perm :permission/projects ?project]
                [?perm :permission/valid-from ?time-from]
                [(get-else $ ?perm :permission/valid-until ?time) ?time-until]
                [(<= ?time-from ?time)]
                [(<= ?time ?time-until)]]
              db
              project
              time))))

(defn user-permission-for-project
  "User and project are either db/ids or lookup refs."
  ([db user project]
   (user-permission-for-project db user project (Date.)))
  ([db user project time]
   (d/q '[:find (pull ?p [*])
          :in $ ?user ?proj ?time
          :where
          [?user :user/permissions ?p]
          [?p :permission/projects ?proj]
          [?p :permission/valid-from ?time-from]
          [(get-else $ ?p :permission/valid-until ?time) ?time-until]
          [(<= ?time-from ?time)]
          [(<= ?time ?time-until)]]
        db
        user
        project
        time)))

(defn user-permissions
  "All valid permissions for user."
  ([db user]
   (user-permissions db user (Date.)))
  ([db user time]
   (mapv first
         (d/q '[:find (pull ?p [:permission/role :permission/projects :permission/scopes
                                :permission/valid-from :permission/valid-until])
                :in $ ?user ?time
                :where
                [?user :user/permissions ?p]
                [?p :permission/valid-from ?time-from]
                [(get-else $ ?p :permission/valid-until ?time) ?time-until]
                [(<= ?time-from ?time)]
                [(<= ?time ?time-until)]]
              db user time))))

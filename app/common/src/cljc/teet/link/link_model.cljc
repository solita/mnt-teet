(ns teet.link.link-model
  "Generic links between entities. Contains specifications about types
  of from entities and the types of links."
  (:require [teet.user.user-model :as user-model]))

(def links-from-pattern
  "Pull pattern to fetch link info originating from this entity.
  Fetches data needed for expand-links to operate on."
  {:link/_from [:db/id :link/to :link/type]})

(def from-types
  "Defines the types of entities that can be the source of the link.
Specifies how a project is resolved from the link and what types of
links can be added."
  {:meeting-agenda {:path-to-project [:meeting/_agenda :activity/_meetings 0
                                      :thk.lifecycle/_activities 0
                                      :thk.project/_lifecycles 0 :db/id]
                    :allowed-link-types #{:task :cadastral-unit :estate}}
   :meeting-decision {:path-to-project [:meeting.agenda/_decisions
                                        :meeting/_agenda :activity/_meetings 0
                                        :thk.lifecycle/_activities 0
                                        :thk.project/_lifecycles 0 :db/id]
                      :allowed-link-types #{:task :cadastral-unit :estate}}})

(def link-types
  "Defines what types of links can be added. Each link type
defines what fields are pulled for display when fetching the
linked entity."
  {:task {:display-attributes [:task/type
                               {:task/assignee [:user/given-name :user/family-name]}
                               :task/estimated-end-date
                               {:activity/_tasks [:db/id :activity/name]}]}})

(def common-link-target-attributes [:meta/deleted? :meta/modified-at
                                    {:meta/modifier user-model/user-display-attributes}])

(defn valid-from? [from]
  (and (vector? from)
       (= 2 (count from))
       (keyword? (first from))))

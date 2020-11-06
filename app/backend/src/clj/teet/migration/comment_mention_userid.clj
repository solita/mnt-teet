(ns teet.migration.comment-mention-userid
  "Change comment mentions to use stable :user/id instead of :db/id"
  (:require [clojure.string :as str]
            [datomic.client.api :as d]
            [teet.log :as log]
            [teet.util.datomic :as du]))

;; Match and replace mentions like "@[user name](user db id)"
;; with :user/id UUID
(defn replace-comment-mention-ids [txt id->uuid]
  (str/replace txt
               #"@\[([^\]]+)\]\((\d+)\)"
               (fn [[_ name id]]
                 (str "@[" name "](" (str (id->uuid id)) ")"))))

;; "hei @[Benjamin Boss](92358976733956) mitä kuuluu?"
(defn rewrite-comment-mentions! [conn]
  (let [db (d/db conn)
        comments (map first
                      (d/q '[:find (pull ?c [:db/id :comment/comment])
                             :where
                             [?c :comment/comment ?txt]
                             [(clojure.string/includes? ?txt "@[")]]
                           db))
        id->uuid (memoize (fn [id]
                            (println "ID: " (pr-str id))
                            (let [uuid (:user/id (du/entity db (Long/parseLong id)))]
                              (println "ID" id  " => UUID" uuid)
                              uuid)))]
    (log/info "Rewriting " (count comments) " with user mentions")
    (d/transact conn
     {:tx-data (vec (for [{id :db/id txt :comment/comment} comments
                          :let [new-txt (replace-comment-mention-ids txt id->uuid)]]
                      {:db/id id
                       :comment/comment new-txt}))})))

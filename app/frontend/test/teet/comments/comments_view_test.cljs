(ns teet.comments.comments-view-test
  (:require [cljs.test :as t :include-macros true]
            [teet.comments.comments-view :as comments-view]
            [teet.drtest :refer [step] :as drt :include-macros true]
            [tuck.core :as tuck]))

(defn test-view [e! {:keys [entity-type entity-id] :as app}]
  [comments-view/lazy-comments {:e! e!
                                :app app
                                :entity-type entity-type
                                :entity-id entity-id}])

(drt/define-drtest lazy-comments-rendering
  {:initial-context {:app (drt/atom {:entity-type :test
                                     :entity-id "1"})}}

  drt/init-step

  (step :tuck-render "Render lazy comments"
        :component test-view)

  (step :expect "Lazy comments loading skeleton is shown"
        :selector ".lazy-comments-loading")

  (step :wait-query "Fetch comments query is run"
        :query :comment/fetch-comments
        :args {:eid "1"
               :for :test}
        :response [{:db/id 420
                    :comment/author #:user {:given-name "Com" :family-name "Menter"}
                    :comment/comment "This is my story and I'm sticking to it"
                    :comment/timestamp #inst "2020-08-18T05:31:04.405-00:00"
                    :comment/files []
                    :comment/visibility {:db/ident :comment.visibility/all}}
                   {:db/id 666
                    :comment/author #:user {:given-name "An" :family-name "Other"}
                    :comment/comment "What a fine test this is"
                    :comment/files []
                    :comment/visibility {:db/ident :comment.visibility/all}}])

  (step :expect "1st comment present"
        :selector "#comment-420")
  (step :expect "2nd comment present"
        :selector "#comment-666"))

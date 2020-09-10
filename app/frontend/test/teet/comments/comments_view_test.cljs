(ns teet.comments.comments-view-test
  (:require [cljs.test :as t :include-macros true]
            [teet.comments.comments-view :as comments-view]
            [teet.drtest :refer [step] :as drt :include-macros true]
            [tuck.core :as tuck]
            [teet.ui.project-context :as project-context]
            [teet.authorization.authorization-check :as authorization-check]))

(def project-eid 123123123)
(def test-user
  {:user/permissions
   [#:permission{:role :internal-consultant,
                 :projects [#:db{:id project-eid}],
                 :valid-from #inst "2020-08-24T09:30:34.141-00:00"}],
   :user/family-name "Consultant",
   :user/id #uuid "fa8af5b7-df45-41ba-93d0-603c543c8801",
   :roles nil,
   :user/given-name "Irma I.",
   :user/person-id "EE12345678955",
   :db/id 321321321,
   :user/email "irma.i.consultant@example.com"})

(def test-comments [{:db/id 420
                     :comment/author #:user {:given-name "Com" :family-name "Menter"}
                     :comment/comment "This is my story and I'm sticking to it"
                     :comment/timestamp #inst "2020-08-18T05:31:04.405-00:00"
                     :meta/creator test-user
                     :comment/files []
                     :comment/visibility {:db/ident :comment.visibility/all}}
                    {:db/id 666
                     :comment/author #:user {:given-name "An" :family-name "Other"}
                     :comment/comment "What a fine test this is"
                     :comment/files []
                     :comment/visibility {:db/ident :comment.visibility/all}}])


(defn test-view [e! {:keys [entity-type entity-id] :as app}]

  (project-context/provide {:db/id project-eid :thk.project/id "4242"}
                           [comments-view/lazy-comments {:e! e!
                                                         :app app
                                                         :entity-type entity-type
                                                         :entity-id entity-id}]))

(drt/define-drtest lazy-comments-rendering
  {:initial-context {:app (drt/atom {:entity-type :test
                                     :entity-id "1"
                                     :page :project
                                     :route {:project {:db/id project-eid}}
                                     :user  test-user
                                     })}}

  (step :tuck-render "Render lazy comments"
        :component test-view)

  (step :expect "Lazy comments loading skeleton is shown"
        :selector ".lazy-comments-loading")

  (step :wait-query "Fetch comments query is run"
        :query :comment/fetch-comments
        :args {:eid "1"
               :for :test}
        :response test-comments)

  (step :expect "1st comment present"
        :selector "#comment-420")
  (step :expect "2nd comment present"
        :selector "#comment-666"))

(drt/define-drtest delete-comment
  {:initial-context {:app (drt/atom {:entity-type :test
                                     :entity-id "1"
                                     :comments-for-entity {"1" test-comments}})}}

  (step :with-authorization "authorize deletion"
        :fn (fn [action entity]
              ;(println "auth" action " for " entity)
              (= action :comment/delete-comment)))

  (step :tuck-render "Render lazy comments"
        :component test-view)

  (step :click "click delete on 1st comment"
        :selector "#delete-button-420")

  (step :wait "for modal to appear" :ms 500)


  (step :click "click modal confirm (outside of container in body)"
        :selector "#confirm-delete"
        :in js/document.body)

  (step :wait-command
        :command :comment/delete-comment
        :payload {:comment-id 420}
        :response true)

  (step :wait-query
        :query :comment/fetch-comments
        :args {:eid "1" :for :test})

  ^{:drtest.step/label "Cleanup"}
  (fn [_]
    (reset! authorization-check/test-authorize nil)
    true))

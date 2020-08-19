(ns teet.comments.comments-view-test
  (:require [cljs.test :as t :include-macros true]
            [teet.comments.comments-view :as comments-view]
            [teet.drtest :refer [step] :as drt :include-macros true]
            [tuck.core :as tuck]
            [teet.authorization.authorization-check :as authorization-check]))

(def test-comments [{:db/id 420
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

(defn test-view [e! {:keys [entity-type entity-id] :as app}]
  [comments-view/lazy-comments {:e! e!
                                :app app
                                :entity-type entity-type
                                :entity-id entity-id}])

(drt/define-drtest lazy-comments-rendering
  {:initial-context {:app (drt/atom {:entity-type :test
                                     :entity-id "1"})}}

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

  ;; FIXME: why doesn't this work, but the manual one does?
  #_(step :click "click delete on 1st comment"
        :selector "#delete-button-420")

  (fn [{c :drtest.step/container :as ctx}]
    (println "KONTEKSTI " ctx)
    (let [b (.querySelector c "#delete-button-420")]
      (println "NAPPI: " b)
      (.click b)
      true))

  (step :wait "for modal to appear" :ms 500)

  ^{:drtest.step/label "Click on modal confirm (outside container in body)"}
  (fn [_]
    (if-let [b (.querySelector js/document.body "#confirm-delete")]
      (do (.click b)
          true)
      false))

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

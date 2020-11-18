(ns teet.meeting.meeting-view-test
  (:require [cljs.test :as t :include-macros true]
            [teet.meeting.meeting-view :as meeting-view]
            [teet.drtest :refer [step] :as drt :include-macros true]
            [tuck.core :as tuck]
            [teet.ui.authorization-context :as authorization-context]
            [teet.meeting.meeting-model :as meeting-model]))

(def project-eid 123123123)
(def meeting-id 22222222)
(def user-id 321321321)
(def user-id2 4444444)
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
   :db/id user-id,
   :user/email "irma.i.consultant@example.com"})

(def test-user-no-edit-rights
  {:user/permissions
   [#:permission{:role :internal-consultant,
                 :projects [#:db{:id project-eid}],
                 :valid-from #inst "2020-08-24T09:30:34.141-00:00"}],
   :user/family-name "Consultant",
   :user/id #uuid"d4c393e6-86af-4dc7-a187-bc5a22019c8b",
   :roles nil,
   :user/given-name "Carol I.",
   :user/person-id "EE11111111111",
   :db/id user-id2,
   :user/email "Carol.i.consultant@example.com"})

(def meeting {:db/id meeting-id
              :meeting/end (js/Date.)
              :meeting/start (js/Date.)
              :meeting/organizer test-user
              :meeting/title "Test meeting"
              :meeting/agenda [{:db/id 1122
                                :meeting.agenda/body "# Will the road be build?\n2. we will find out in this exiting meeting\n"
                                :meeting.agenda/topic "What about this road?!"}]
              :meeting/location "Location for test"
              :participation/_in [{:db/id 123123123
                                   :participation/participant test-user
                                   :participation/role {:db/id 83562883711807
                                                        :db/ident :participation.role/organizer}}
                                  {:db/id 13123
                                   :participation/participant test-user-no-edit-rights
                                   :participation/role {:db/id 81241807
                                                        :db/ident :participation.role/participant}}]})

(def new-agenda-topic "New agenda topic")

(defn meeting-view
  [e! app]
  [:div
   [authorization-context/with
    (when (meeting-model/user-is-organizer-or-reviewer? (:user app) meeting)
      #{:edit-meeting})
    [meeting-view/meeting-main-content e! app (:meeting app)]]])

(drt/define-drtest add-agenda-topic
  {:initial-context {:app (drt/atom {:page :meeting
                                     :meeting meeting
                                     :user test-user})}}

  (step :tuck-render "meeting-view"
        :component meeting-view)

  (step :expect "Show create agenda button, because user is organizer"
        :selector "#add-agenda")

  (step :click "Open add agenda modal"
        :selector "#add-agenda")

  (step :wait "for modal to appear" :ms 500)

  (step :type "Type in the topic field of the form"
        :selector "#agenda-title"
        :in js/document.body
        :text new-agenda-topic
        )

  (step :click "click save on agenda form"
        :selector ".submit"
        :in js/document.body)

  (step :wait-command
        :command :meeting/update-agenda
        :predicate (fn [{:keys [payload]}]
                     (= (get-in payload [:meeting/agenda 0 :meeting.agenda/topic]) new-agenda-topic))
        :response true)
  )


(drt/define-drtest user-cannot-add-topic
  {:initial-context {:app (drt/atom {:page :meeting
                                     :meeting meeting
                                     :user test-user-no-edit-rights})}}
  (step :tuck-render "meeting-view"
        :component meeting-view)

  (step :expect-no "That there is no add agenda button for meeting because user is not a reviewer or an organizer"
        :selector "#add-agenda"))



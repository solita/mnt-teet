(ns teet.cooperation.cooperation-controller
  (:require [tuck.core :as t]
            [taoensso.timbre :as log]

            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.common.common-controller :as common-controller]))

(defrecord ThirdPartyCreated [name save-response])
(defrecord ApplicationCreated [save-response])

(defrecord ResponseCreated [response edit?])
(defrecord OpinionSaved [new? response])
(defrecord DeleteOpinion [opinion])
(defrecord DeleteOpinionSuccess [opinion])

;; delete opinion todo / impl notes
;;  - make controller call the command when delete button is used [ ]
;;     - plan a: add delete-button-with-confirm component with straightforward DeleteOpinion tuck event
;;       - common-controller/SaveForm prob: request doesn't fire judging by network tab even though SaveForm is called.
;;       - debug log in button widget is triggered though.
;;       - remains a mystery, switch approaches
;;     - plan b - imitate edit-opinion button and use form/form-modal-button. get backend call working first and then mod towards delete button look
;;       - has the problem that it opens up a whole another form
;;       - try to look for other similar ? maybe later, try plan c now
;;     - plan c - looks like there's builtin delete button support in form/form-footer
;;       - figure out how to use that
;;       - add a comment block to cooperation view start that shows the nesting structure of components to discern form vs form2 usage
;;         - turns out was unnecessary, delete support in both form types, but useful doc still
;;         - does the spec on ticket allow using this? -> yes
;;         - do we have a nearby form/form parent? well not a parent but a child. opinion-form is passed by edit-opinion component
;;        - circumstancial evidence: edit-opinion component has this form-component/form-value set of attrs and the opening modal has cancel & save buttons without having been specified
;;            - debug prints in default form-footer component revealed it's being used here somehow
;;         - need to figure where how to get to the footer args. footer is passed to form/form fn explicitly from somewhere
;;           - footer2 picks up :footer key from context so maybe we could put :delete style keys there?
;;           - yep - pprinting the :footer stuff from under context key :form shows cancel and validate handlers defined in the context defined in form/form2 fn
;;           - if we could provide args for the form2 fn call there we could define a :delete callback.. and form/form (used by opinion-form) seems to pass its opts straight to form/form2. let's try this..
;;           - actually there's a :delete usage in cooperation-view/application-people-panel, missed this earlier, try along that
;;             - its :delete attr defn is puzzling, result of straight ->SaveForm call not wrapped in a fn
;;             - also doesn't show a delete button when defined like that, but a normal fn there does
;;             - experimenting, even w debug prints in SaveForm impl and opinion-form.. somehow SaveForm doesn't get called there right away, and putting in the do block the attr defn doesn't either..
;;             - but somehow it works, ok, let's go with this
;;                - the record returned / created by ->SaveForm is just data, the process-event fn will be called later when it gets the event or something along those lines
;;             - need to figure out how to close the edit form after delete
;;                - try accessing the :cancel fn from context? nope
;;                - actually we have the close-event param on hand, use that
;;                - but first need to have a command to call successfully i guess
;;  - ui side authorization? no need to do anything since edit form opens only for edit authorized users.
;;  - implement delete command on backend [x]
;;       - edit-application perm checked
;;  - ensure opinion disappears from app (or reload state from backend) after delete success [ ]

(extend-protocol t/Event
  ThirdPartyCreated
  (process-event [{name :name} {params :params :as app}]
    (t/fx (snackbar-controller/open-snack-bar app (tr [:cooperation :new-third-party-created]))
          common-controller/refresh-fx))

  ApplicationCreated
  (process-event [{r :save-response} {params :params :as app}]
    (let [{:keys [third-party-teet-id application-teet-id]} r]
      (t/fx (snackbar-controller/open-snack-bar app (tr [:cooperation :new-application-created]))
            {:tuck.effect/type :navigate
             :page :cooperation-application
             :params {:project (:project params)
                      :third-party third-party-teet-id
                      :application application-teet-id}})))

  ResponseCreated
  (process-event [{r :response
                   edit? :edit?} {params :params :as app}]
    (t/fx (snackbar-controller/open-snack-bar
           app
           (if edit?
             (tr [:cooperation :response-saved])
             (tr [:cooperation :response-created])))
          common-controller/refresh-fx))

  OpinionSaved
  (process-event [{new? :new? r :response} app]
    (t/fx (snackbar-controller/open-snack-bar
           app
           (tr [:cooperation (if new? :opinion-created :opinion-saved)]))
          common-controller/refresh-fx)))

(defn save-opinion-event [application form close-event]
  (common-controller/->SaveForm
   :cooperation/save-opinion
   {:application-id (:db/id application)
    :opinion-form form}
   (fn [response]
     (fn [e!]
       (e! (close-event))
       (e! (->OpinionSaved (not (contains? form :db/id)) response))))))

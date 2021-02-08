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

;; delete opinion todo
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
;;            - need to figure where how to get to the footer args. footer is passed to form/form fn explicitly from somewhere
;;              - footer2 picks up :footer key from context so maybe we could put :delete style keys there?
;;              - yep - pprinting the :footer stuff from under context key :form shows cancel and validate handlers defined in the context defined in form/form2 fn
;;              - if we could provide args for the form2 fn call there we could define a :delete callback.. and form/form (used by opinion-form) seems to pass its opts straight to form/form2. let's try this..
;;              - actually there's a :delete usage in cooperation-view/application-people-panel, missed this earlier, try along that

;;            - edit-opinion coimpoentn contains form/form-modal-button component. this makes a panels/modal component as parent of the passed-in form-component (here opinion-form).
;;
;;  - implement delete command on backend [ ]
;;  - ensure opinion disappeasr from app (or reload state from backend) after delete success [ ]




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

  DeleteOpinion
  (process-event [{opinion :opinion} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :cooperation/delete-opinion
           :payload {:cooperation/opinion-id (:db/id opinion)}
           :success-message (tr [:notifications :opinion-deleted])
           :result-event ->DeleteOpinionSuccess
           }))

  DeleteOpinionSuccess
  (process-event [{opinion :opinion} {:keys [params] :as app}]
    ;; need to remove opinion from app db by id and keep view the same (refresh fx?)
    )

  
  
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

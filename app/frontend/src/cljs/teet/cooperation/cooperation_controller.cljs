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
;;     - common-controller/SaveForm prob: request doesn't fire judging by network tab even though SaveForm is called.
;;     - debug log in button widget is triggered though.
;;     - remains a mystery, switch approaches
;;     - plan b - imitate edit-opinion button and use form/form-modal-button. get backend call working first and then mod towards delete button look
;;     - plan c - looks like there's builtin delete button support in eg form/form-footer - figure out how to use that
;;  - implement delete commadn on backend
;;  - make opinion disappear from app (or reload state from backend) after delete success
;;  - check appearance of delete button - test with red version

(defn delete-opinion-action [opinion]
  ;; this is the :action fn of the delete-button for deleting an opinion
  (log/debug "in delete-opinion-action")
  (if-let [id (:db/id opinion)]
    (do
      (log/debug "about to invoke ->SaveForm2")
      (common-controller/->SaveForm2 :cooperation/delete-opinion
                                    {:opinion-id id}
                                    (fn [_response]
                                      ;; need to do anything here?
                                      (log/info "in delete-opinion SaveForm response callback")
                                      common-controller/refresh-fx)))
    ;; else
    (log/debug "delete-opinion-action: no :db/id in passed opinion (" (pr-str opinion) ")")))

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

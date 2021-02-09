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

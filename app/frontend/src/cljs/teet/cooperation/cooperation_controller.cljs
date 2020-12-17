(ns teet.cooperation.cooperation-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.common.common-controller :as common-controller]
            [teet.ui.rich-text-editor :as rich-text-editor]
            [teet.util.collection :as cu]))

(defrecord ThirdPartyCreated [name save-response])
(defrecord ApplicationCreated [save-response])

(defrecord ResponseCreated [response edit?])
(defrecord DecisionCreated [response])

(extend-protocol t/Event

  ThirdPartyCreated
  (process-event [{name :name} {params :params :as app}]
    (t/fx (snackbar-controller/open-snack-bar app (tr [:cooperation :new-third-party-created]))
          common-controller/refresh-fx))

  ApplicationCreated
  (process-event [{r :save-response} {params :params :as app}]
    (let [application-id (get-in r [:tempids "new-application"])]
      (t/fx (snackbar-controller/open-snack-bar app (tr [:cooperation :new-application-created]))
            {:tuck.effect/type :navigate
             :page :cooperation-application
             :params {:project (:project params)
                      :third-party (js/decodeURIComponent (:third-party params))
                      :application (str application-id)}})))

  ResponseCreated
  (process-event [{r :response
                   edit? :edit?} {params :params :as app}]
    (t/fx (snackbar-controller/open-snack-bar app (if edit?
                                                    (tr [:cooperation :response-edited])
                                                    (tr [:cooperation :new-response-created])))
          common-controller/refresh-fx))

  DecisionCreated
  (process-event [{r :response} app]
    (t/fx (snackbar-controller/open-snack-bar
           app
           (tr [:cooperation :decision-created]))
          common-controller/refresh-fx)))

(defn prepare-decision-form [form]
  (-> form
      (update :cooperation.position/comment
              #(when % (rich-text-editor/editor-state->markdown %)))
      cu/without-nils))

(defn save-decision-event [application form close-event]
  (common-controller/->SaveForm
   :cooperation/save-decision
   {:application-id (:db/id application)
    :decision-form (prepare-decision-form form)}
   (fn [response]
     (fn [e!]
       (e! (close-event))
       (e! (->DecisionCreated response))))))

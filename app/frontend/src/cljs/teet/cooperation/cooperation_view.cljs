(ns teet.cooperation.cooperation-view
  (:require [teet.project.project-view :as project-view]
            [teet.ui.url :as url]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.material-ui :refer [Card CardHeader CardContent]]
            [teet.ui.common :as common]
            [teet.ui.format :as format]
            [reagent.core :as r]
            [teet.ui.buttons :as buttons]
            [teet.ui.form :as form]
            [teet.cooperation.cooperation-controller :as cooperation-controller]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.text-field :as text-field]
            [teet.common.common-controller :as common-controller]))


(defn- third-party-form [{:keys [e! project-id]} close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel-event close-event
              :save-event #(common-controller/->SaveForm
                            :cooperation/create-3rd-party
                            {:thk.project/id project-id
                             :third-party @form-atom}
                            (fn [response]
                              (fn [e!]
                                (e! (close-event))
                                (e! (cooperation-controller/->ThirdPartyCreated
                                     (:cooperation.3rd-party/name @form-atom)
                                     response)))))
              :spec ::cooperation-model/third-party-form}
   ^{:attribute :cooperation.3rd-party/name}
   [text-field/TextField {}]

   ^{:attribute :cooperation.3rd-party/id-code}
   [text-field/TextField {}]

   ^{:attribute :cooperation.3rd-party/email}
   [text-field/TextField {}]

   ^{:attribute :cooperation.3rd-party/phone}
   [text-field/TextField {}]])

(defn- third-parties [e! project third-parties]
  [:div
   (for [{id :db/id
          :cooperation.3rd-party/keys [name]} third-parties]
     ^{:key (str id)}
     [url/Link {:page :cooperation-third-party
                :params {:third-party (js/encodeURIComponent name)}}
      name])
   [form/form-modal-button
    {:max-width "sm"
     :form-component [third-party-form {:e! e!
                                        :project-id (:thk.project/id project)}]
     :modal-title (tr [:cooperation :new-third-party-title])
     :button-component [buttons/button-primary {}
                        (tr [:cooperation :new-third-party])]}]])

(defn- cooperation-page-structure [e! app project third-parties-list main-content]
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :left-panel [third-parties e! project third-parties-list]
    :main main-content}])

(defn overview-page [e! {:keys [user] :as app} {:keys [project overview]}]
  [cooperation-page-structure
   e! app project overview
   [:<>
    [typography/Heading2 (tr [:cooperation :page-title])]
    [typography/BoldGreyText (tr [:cooperation :all-third-parties])]
    (pr-str overview)]])

(defn- applications [{:cooperation.3rd-party/keys [applications] :as third-party}]
  [:<>
   (for [{id :db/id
          :cooperation.application/keys [date type response-type response]
          :as appl} applications]
     ^{:key (str id)}
     [Card {}
      ;; Header shows type of application and response (as link to application page)
      ;; activity for the application
      [CardHeader {:title (r/as-element
                           [url/Link {:page :cooperation-application
                                      :params {:application (str id)}}
                            (str
                             (tr-enum type) " / " (tr-enum response-type))])}]

      ;; Content shows response, dates
      ;; responsible person
      ;; position of mnt
      [CardContent
       (let [{:cooperation.response/keys [status date valid-until]} response]
         [common/basic-information-row
          [[(tr [:fields :cooperation.response/status])
            ;; colored circle based on status
            (tr-enum status)]
           [(tr [:fields :cooperation.response/date])
            (format/date date)]
           [(tr [:fields :cooperation.response/valid-until])
            (format/date valid-until)]]])]])])

(defn- application-form [{:keys [e! project-id third-party]} close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel-event close-event
              :save-event #(common-controller/->SaveForm
                            :cooperation/create-application
                            {:thk.project/id project-id
                             :cooperation.3rd-party/name third-party
                             :application @form-atom}
                            (fn [response]
                              (fn [e!]
                                (e! (close-event))
                                (e! (cooperation-controller/->ApplicationCreated response)))))
              :spec ::cooperation-model/application-form}
   ^{:attribute :cooperation.application/type}
   [select/select-enum {:e! e! :attribute :cooperation.application/type}]

   ^{:attribute :cooperation.application/response-type}
   [select/select-enum {:e! e! :attribute :cooperation.application/response-type}]

   ^{:attribute :cooperation.application/date
     :xs 8}
   [date-picker/date-input {}]

   ^{:attribute :cooperation.application/response-deadline
     :xs 8}
   [date-picker/date-input {}]

   ^{:attribute :cooperation.application/comment}
   [text-field/TextField {:multiline true
                          :rows 5}]])

(defn third-party-page [e! {:keys [user params] :as app} {:keys [project overview]}]
  (let [third-party-name (js/decodeURIComponent (:third-party params))
        third-party (some #(when (= third-party-name
                                    (:cooperation.3rd-party/name %)) %)
                          overview)]
    [cooperation-page-structure
     e! app project overview
     [:<>
      [common/header-with-actions (:cooperation.3rd-party/name third-party)]
      [form/form-modal-button {:max-width "sm"
                               :form-component [application-form {:e! e!
                                                                  :project-id (:thk.project/id project)
                                                                  :third-party third-party-name}]
                               :modal-title (tr [:cooperation :new-application-title])
                               :button-component [buttons/button-primary {}
                                                  (tr [:cooperation :new-application])]}]
      [applications third-party]]]))

(defn application-page [e! app {:keys [project overview third-party]}]
  (let [application (get-in third-party [:cooperation.3rd-party/applications 0])]
    [cooperation-page-structure
     e! app project overview
     [:<>
      [:br]
      "3rd party:" (pr-str third-party)
      [:br]
      "application:" (pr-str application)]]))

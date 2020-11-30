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
  [:div.project-third-parties-list
   (for [{id :db/id
          :cooperation.3rd-party/keys [name]} third-parties]
     ^{:key (str id)}
     [:div.project-third-party
      [url/Link {:page :cooperation-third-party
                 :params {:third-party (js/encodeURIComponent name)}}
       name]])
   [form/form-modal-button
    {:max-width "sm"
     :form-component [third-party-form {:e! e!
                                        :project-id (:thk.project/id project)}]
     :modal-title (tr [:cooperation :new-third-party-title])
     :button-component [buttons/button-primary {:class :new-third-party}
                        (tr [:cooperation :new-third-party])]}]])

(defn- cooperation-page-structure [e! app project third-parties-list main-content]
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :left-panel [third-parties e! project third-parties-list]
    :main main-content}])

(defn- application-link [{id :db/id :cooperation.application/keys [type response-type]}]
  [url/Link {:page :cooperation-application
             :params {:application (str id)}}
   (str
    (tr-enum type) " / " (tr-enum response-type))])

(defn- application-card-content [{:cooperation.application/keys [response]}]
  (let [{:cooperation.response/keys [status date valid-until]} response]
    [common/basic-information-row
     [[(tr [:fields :cooperation.response/status])
       ;; colored circle based on status
       (tr-enum status)]
      [(tr [:fields :cooperation.response/date])
       (format/date date)]
      [(tr [:fields :cooperation.response/valid-until])
       (format/date valid-until)]]]))

(defn overview-page [e! app {:keys [project overview]}]

  [:span.cooperation-overview-page
   [cooperation-page-structure
    e! app project overview
    [:<>
     [typography/Heading2 (tr [:cooperation :page-title])]
     [typography/BoldGreyText (tr [:cooperation :all-third-parties])]
     (for [{:cooperation.3rd-party/keys [name applications] :as tp} overview]
       [Card {}
        [CardHeader {:title (r/as-element
                             [url/Link {:page :cooperation-third-party
                                        :params {:third-party (js/encodeURIComponent name)}}
                              name])}]
        (when-let [application (first applications)]
          [CardContent {}
           [url/with-navigation-params
            {:third-party (js/encodeURIComponent name)}
            [application-link application]]
           [application-card-content application]])])]]])

(defn- applications [{:cooperation.3rd-party/keys [applications] :as _third-party}]
  [:<>
   (for [{id :db/id
          :cooperation.application/keys [response]
          :as application} applications]
     ^{:key (str id)}
     [Card {}
      ;; Header shows type of application and response (as link to application page)
      ;; activity for the application
      [CardHeader {:title (r/as-element [application-link application])}]

      ;; Content shows response, dates
      ;; responsible person
      ;; position of mnt
      [CardContent
       [application-card-content application]]])])

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

(defn third-party-page [e! {:keys [params] :as app} {:keys [project overview]}]
  (let [third-party-name (js/decodeURIComponent (:third-party params))
        third-party (some #(when (= third-party-name
                                    (:cooperation.3rd-party/name %)) %)
                          overview)]
    [:span.cooperation-third-party-page
     [cooperation-page-structure
      e! app project overview
      [:<>
       [common/header-with-actions (:cooperation.3rd-party/name third-party)]
       [form/form-modal-button
        {:max-width "sm"
         :form-component [application-form {:e! e!
                                            :project-id (:thk.project/id project)
                                            :third-party third-party-name}]
         :modal-title (tr [:cooperation :new-application-title])
         :button-component [buttons/button-primary {:class :new-application}
                            (tr [:cooperation :new-application])]}]
       [applications third-party]]]]))

(defn- application-response-form [_close-event _form-atom]
  [:div "TODO: application response form"])

(defn application-page [e! app {:keys [project overview third-party]}]
  (let [application (get-in third-party [:cooperation.3rd-party/applications 0])]
    [:span.cooperation-application-page
     [cooperation-page-structure
      e! app project overview
      [:<>
       [common/header-with-actions (:cooperation.3rd-party/name third-party)]
       [typography/Heading2
        (tr-enum (:cooperation.application/type application)) " / "
        (tr-enum (:cooperation.application/response-type application))]
       ;; FIXME: show activity name as subheading
       [:br]
       [form/form-modal-button
        {:max-width "sm"
         :form-component [application-response-form]
         :button-component [buttons/button-primary {:class :enter-response}
                            (tr [:cooperation :enter-response])]}]
       [:br]
       "3rd party:" (pr-str third-party)
       [:br]
       "application:" (pr-str application)]]]))

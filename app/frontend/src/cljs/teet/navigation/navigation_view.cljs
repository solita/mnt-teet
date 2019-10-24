(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]

            [teet.routes :as routes]
            [teet.ui.material-ui :refer [AppBar Toolbar Drawer List ListItem
                                         ListItemText ListItemIcon
                                         FormControl InputLabel Select Link]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-logo :as navigation-logo]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [herb.core :as herb :refer [<class]]))

(defn language-selector
  []
  (let [label "Language"
        id "language-select"
        name "language"
        value (case @localization/selected-language
                :et
                {:value "et" :label (get localization/language-names "et")}
                :en
                {:value "en" :label (get localization/language-names "en")})
        required false
        items [{:value "et" :label (get localization/language-names "et")}
               {:value "en" :label (get localization/language-names "en")}]
        show-empty-selection? false
        format-item :label
        error nil
        on-change (fn [val]
                    (localization/load-language!
                     (keyword (:value val))
                     (fn [language _]
                       (reset! localization/selected-language
                               language))))]
    (r/with-let [reference (r/atom nil)
                 set-ref! (fn [el]
                            (reset! reference el))]
      (let [option-idx (zipmap items (range))
            change-value (fn [e]
                           (let [val (-> e .-target .-value)]
                             (if (= val "")
                               (on-change nil)
                               (on-change (nth items (int val))))))]
        [:div {:class (herb/join (<class navigation-style/language-select-container-style)
                                 (<class navigation-style/divider-style))}
         [FormControl {:variant  :standard
                       :required required
                       :error    error}
          [InputLabel {:html-for id
                       :ref      set-ref!}
           (str label ":")]
          [Select
           {:value       (or (option-idx value) "")
            :name        name
            :native      true
            :required    (boolean required)
            :label-width (or (some-> @reference .-offsetWidth) 12)
            :input-props {:id   id
                          :name name}
            :on-change   (fn [e]
                           (change-value e))
            :classes {:root (<class navigation-style/language-select-style)}}
           (when show-empty-selection?
             [:option {:value ""}])
           (doall
            (map-indexed
             (fn [i item]
               [:option {:value i
                         :key   i} (format-item item)])
             items))]]]))))

(defn- drawer-header
  [e! open?]
  [ListItem {:component "button"
             :align-items "center"
             :button true
             :on-click #(e! (navigation-controller/->ToggleDrawer))
             :classes {:root (<class navigation-style/drawer-list-item-style false)}}
   [ListItemIcon {:style {:display :flex
                          :justify-content :center}}
    (if open?
      [icons/navigation-close]
      [icons/navigation-menu])]
   (when open?
     [ListItemText {:primary (tr [:common :hide-menu])}])])

(defn- view-link [{:keys [open? current-page link icon name]}]
  (let [current-page? (= current-page (:page link))]
    [ListItem {:component "a"
              :href (routes/url-for link)
              :align-items "center"
              :button true
              :classes {:root (<class navigation-style/drawer-list-item-style current-page?)}}
    [ListItemIcon {:style {:display :flex
                           :justify-content :center}}
     [icon]]
    (when open?
      [ListItemText {:primary name}])]))

(defn- page-listing
  [e! open? page]
  [List {:class (<class navigation-style/page-listing)}
   [drawer-header e! open?]
   [view-link {:open? open?
               :current-page page
               :link {:page :root}
               :icon icons/maps-map
               :name (tr [:projects :map-view])}]
   [view-link {:open? open?
               :current-page page
               :link {:page :projects-list}
               :icon icons/action-list
               :name (tr [:projects :list-view])}]
   [view-link {:open? open?
               :current-page page
               :link {:page :road
                      :query {:road 1
                              :carriageway 1
                              :start-m 100
                              :end-m 17000}}
               :icon icons/maps-my-location
               :name "Road location"}]
   [view-link {:open? open?
               :current-page page
               :link {:page :components}
               :icon icons/content-archive
               :name "Components"}]])

(defn user-info [user]
  [:div {:class (<class navigation-style/divider-style)}
   [:div {:class (<class navigation-style/user-info-style)}
    [typography/Text {:classes {:root (<class navigation-style/user-label-style)}}
     "My role:"]
    [typography/Text {:classes {:root (<class navigation-style/user-role-style)}}
     ;; TODO: Show actual role once these are figured out
     (:user/family-name user)]]])

(defn logout [e!]
  [:div {:class (herb/join (<class navigation-style/logout-container-style)
                           (<class navigation-style/divider-style))}
   [Link {:underline :always
          :class (<class navigation-style/logout-style)
          :href "/#/login"}
    "Log out"]])

(defn header
  [e! {:keys [open? page quick-search]} user]
  [:<>
   [AppBar {:position "sticky"
            :className (herb/join (<class navigation-style/appbar)
                                  (<class navigation-style/appbar-position open?))}
    [Toolbar {:className (herb/join (<class navigation-style/toolbar))}
     [:div {:class (<class navigation-style/logo-style)}
      navigation-logo/maanteeamet-logo]
     [search-view/quick-search e! quick-search]
     [language-selector]
     [user-info user]
     [logout e!]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [page-listing e! open? page]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])

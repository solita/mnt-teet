(ns teet.asset.asset-ui
  "Common asset related UI components"
  (:require [clojure.string :as str]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.asset.cost-items-map-view :as cost-items-map-view]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum] :as localization]
            [teet.project.project-view :as project-view]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.container :as container]
            [teet.ui.context :as context]
            [teet.ui.form :as form]
            [teet.ui.format :as fmt]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid CircularProgress Link]]
            [teet.ui.panels :as panels]
            [teet.ui.project-context :as project-context]
            [teet.ui.query :as query]
            [teet.ui.select :as select]
            [teet.ui.text-field :as text-field]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.util.string :as string]))

(defn tr*
  "Return localized asset schema value for key (default :asset-schema/label)."
  ([m]
   (tr* m :asset-schema/label))
  ([m key]
   (get-in m [key (case @localization/selected-language
                    :et 0
                    :en 1)])))

(defn label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

(defn- label-for* [item rotl]
  [:span (label (rotl item))])

(defn label-for [item]
  [context/consume :rotl [label-for* item]])

(defn- format-fg-and-fc [[fg fc]]
  (if (and (nil? fg)
           (nil? fc))
    ""
    (str (label fg) " / " (label fc))))

(defn- format-fg-and-fc-with-components [[_fg _fc cs :as result]]
  (if (seq cs)
    [:div
     (format-fg-and-fc result)
     (doall
      (for [{:keys [component]} cs]
        [typography/SmallText {:style {:margin-left "2rem"}} component]))]
    (format-fg-and-fc result)))

(defn- format-fc [[_fg fc]]
  (label fc))

(defn format-euro [val]
  (when val
    (str val "\u00A0€")))

(defn select-fgroup-and-fclass [{:keys [e! on-change value atl read-only?]}]
  (let [[fg-ident fc-ident] value
        fg (if fg-ident
             (asset-type-library/item-by-ident atl fg-ident)
             (when fc-ident
               (asset-type-library/fgroup-for-fclass atl fc-ident)))
        fc (and fc-ident (asset-type-library/item-by-ident atl fc-ident))
        fgroups (:fgroups atl)]
    (if read-only?
      [typography/Heading3 (format-fg-and-fc [fg fc])]
      [Grid {:container true}
       [Grid {:item true :xs 12}
        [select/select-search
         {:e! e!
          :on-change #(on-change (mapv :db/ident %))
          :placeholder (tr [:asset :feature-group-and-class-placeholder])
          :no-results (tr [:asset :no-matching-feature-classes])
          :value (when fc [fg fc])
          :format-result format-fg-and-fc-with-components
          :show-empty-selection? true
          :clear-value [nil nil]
          :query (fn [text]
                   #(asset-type-library/search-fclass
                     atl @localization/selected-language text))}]]])))

(defn select-fgroup-and-fclass-multiple
  "Select multiple [fgroup fclass] values."
  [{:keys [e! on-change value atl]}]
  [select/select-search-multiple
   {:e! e!
    :on-change on-change
    :placeholder (tr [:asset :feature-group-and-class-placeholder])
    :no-results (tr [:asset :no-matching-feature-classes])
    :value value
    :format-result format-fg-and-fc-with-components
    :format-result-chip format-fc
    :show-empty-selection? true
    :clear-value [nil nil]
    :query (fn [text]
             #(asset-type-library/search-fclass
               atl @localization/selected-language text))}])

(defn select-listitem-multiple [{:keys [atl attribute]}]
  (let [attr (asset-type-library/item-by-ident atl attribute)
        values (:enum/_attribute attr)]
    (fn [{:keys [e! on-change value]}]
      [select/select-search-multiple
       {:e! e!
        :placeholder (str (label attr) "...")
        :value value
        :on-change on-change
        :format-result label
        :query-threshold 0 ; always do "query"
        :query (fn [text]
                 #(if (str/blank? text)
                    values
                    (into []
                          (filter (fn [v]
                                    (string/contains-words? (label v) text)))
                          values)))}])))

(defn wrap-atl-loader [page-fn e! {atl :asset-type-library :as app} state]
  (if-not atl
    [CircularProgress {}]
    [cost-items-map-view/with-map-context
     app (:project state)
     [page-fn e! app state]]))

(defn- road-nr-format [relevant-roads]
  (let [road-nr->item-name (->> relevant-roads
                                (map (juxt :road-nr
                                           #(str (:road-nr %) " " (:road-name %))))
                                (into {}))]
    (fn [select-value]
      (get road-nr->item-name
           select-value
           ""))))

(defn number-value
  ([opts]
   (number-value [] opts))
  ([extra-opts opts]
   (update opts :value
           #(or
             (some (fn [extra-opt]
                     (when (= (str extra-opt) (str %))
                       extra-opt)) extra-opts)
             (if (string? %)
               (js/parseInt %)
               %)))))

(defn- relevant-road-select* [{:keys [empty-label extra-opts extra-opts-label] :as opts
                               :or {empty-label ""}} relevant-roads]
  (let [items (->> relevant-roads (map :road-nr) sort vec)
        fmt (road-nr-format relevant-roads)]
    [select/form-select
     (->> opts
          (merge {:show-empty-selection? (if extra-opts false true)
                  :empty-selection-label empty-label
                  :items (if extra-opts
                           (into extra-opts items)
                           items)
                  :format-item (if extra-opts
                                 #(or (extra-opts-label %)
                                      (fmt %))
                                 fmt)})
          (number-value extra-opts))]))

(defn- with-relevant-roads* [{e! :e!} _ {project-id :thk.project/id}]
  (e! (cost-items-controller/->FetchRelevantRoads project-id))
  (fn [_ component _]
    (conj component
          (get @cost-items-controller/relevant-road-cache project-id))))

(defn with-relevant-roads [opts component]
  [project-context/consume
   [with-relevant-roads* opts component]])


(defn relevant-road-select [opts]
  [with-relevant-roads opts
   [relevant-road-select* opts]])

(defn- export-boq-form [e! on-close project versions]
  (r/with-let [export-options
               (r/atom {:thk.project/id project
                        :boq-export/unit-prices? true
                        :boq-export/version (first versions)})
               change-event (form/update-atom-event export-options merge)
               format-version (fn [{:boq-version/keys [type number] :as v}]
                                (if-not v
                                  (tr [:asset :unofficial-version])
                                  (str (tr-enum type) " v" number)))]
    [:<>
     [form/form {:e! e!
                 :value @export-options
                 :on-change-event change-event}
      ^{:attribute :boq-export/unit-prices?}
      [select/checkbox {}]

      ^{:attribute :boq-export/version}
      [select/form-select
       {:items (into [nil] versions)
        :format-item format-version}]]

     [:div {:class (<class common-styles/flex-row-space-between)}
      [buttons/button-secondary {:on-click on-close}
       (tr [:buttons :cancel])]
      [buttons/button-primary
       {:element :a
        :target :_blank
        :href (common-controller/query-url
               :asset/export-boq
               (cu/without-nils
                (merge
                 {:boq-export/language @localization/selected-language}
                 (update @export-options
                         :boq-export/version :db/id))))}
       (tr [:asset :export-boq])]]]))

(defn- export-boq-dialog [e! app close-export-dialog!]
  [panels/modal {:title (tr [:asset :export-boq])
                 :on-close close-export-dialog!}
   [query/query
    {:e! e!
     :query :asset/version-history
     :args {:thk.project/id (get-in app [:params :project])}
     :simple-view [export-boq-form e! close-export-dialog!
                   (get-in app [:params :project])]}]])

(defn- cost-items-navigation-select [e! {:keys [page params]}]
  [:div {:class (<class common-styles/padding 1 1)}
   [select/form-select
    {:on-change #(e! (common-controller/->Navigate % params nil))
     :items [:cost-items :cost-items-totals :materials-and-products]
     :value page
     :format-item #(tr [:asset :page %])}]])

(defn- cost-item-hierarchy
  "Show hierarchy of existing cost items, grouped by fgroup and fclass."
  [{:keys [_e! _app cost-items fgroup-link-fn fclass-link-fn list-features?]
    :or {list-features? true}}]
  (r/with-let [open (r/atom #{})
               toggle-open! #(swap! open cu/toggle %)]
    [:div.cost-items-by-fgroup
     (doall
      (for [[{ident :db/ident :as fgroup} fclasses] cost-items
            :let [label (str (tr* fgroup)
                             " (" (apply + (map (comp count val) fclasses)) ")")]]
        ^{:key (str ident)}
        [container/collapsible-container
         {:on-toggle (r/partial toggle-open! ident)
          :open? (contains? @open ident)}
         (if fgroup-link-fn
           [Link {:href (fgroup-link-fn fgroup)} label]
           label)
         [:div.cost-items-by-fclass {:data-fgroup (str ident)
                                     :style {:margin-left "0.5rem"}}
          (doall
           (for [[{ident :db/ident :as fclass} cost-items] fclasses
                 :let [label (str/upper-case (tr* fclass))]]
             ^{:key (str ident)}
             [:div {:style {:margin-top "1rem"
                            :margin-left "1rem"}}
              [typography/Text2Bold
               (if fclass-link-fn
                 [Link {:href (fclass-link-fn fclass)} label]
                 label)]
              (when list-features?
                [:div.cost-items {:style {:margin-left "1rem"}}
                 (for [{oid :asset/oid} cost-items]
                   ^{:key oid}
                   [:div
                    [url/Link {:page :cost-item
                               :params {:id oid}} oid]])])]))]]))]))

(defn- save-boq-version-dialog [{:keys [e! on-close]} last-locked-version]
  (r/with-let [current-type (-> last-locked-version
                                :boq-version/type
                                :db/ident)
               form-state (r/atom {:boq-version/type current-type})
               form-change (form/update-atom-event form-state merge)
               save-event #(cost-items-controller/->SaveBOQVersion on-close @form-state)]
    [panels/modal {:title (tr [:asset :save-boq-version])
                   :subtitle (when (:boq-version/type last-locked-version)
                               (str (tr-enum (:boq-version/type last-locked-version))
                                    " v. "
                                    (:boq-version/number last-locked-version)))
                   :on-close on-close}

     [form/form {:e! e!
                 :value @form-state
                 :on-change-event form-change
                 :save-event save-event
                 :cancel-event (form/callback-event on-close)}
      ^{:attribute :boq-version/type
        :required? true}
      [select/select-enum {:e! e!
                           :attribute :boq-version/type
                           ;; Show "(current)" with the type of latest locked bill of quantities
                           :format-enum-fn (fn [_]
                                             (fn [t]
                                               (str (tr [:enum t])
                                                    (when (= t current-type)
                                                      (str " ("
                                                           (tr [:common :current])
                                                           ")")))))
                           :database :asset}]

      ^{:attribute :boq-version/explanation
        :required? true
        :validate (fn [v]
                    (when (> (count v) 2000)
                      (tr [:asset :validate :max-length] {:max-length 2000})))}
      [text-field/TextField {:multiline true
                             :rows 4}]]]))

(defn- unlock-for-edits-dialog [{:keys [e! on-close version]}]
  [panels/modal {:title (tr [:asset :unlock-for-edits])
                 :on-close on-close}
   [:<>
    (when-let [warn (condp du/enum= (:boq-version/type version)
                      :boq-version.type/tender
                      (tr [:asset :unlock-tender-boq-warning])

                      :boq-version.type/contract
                      (tr [:asset :unlock-contract-boq-warning])

                      nil)]
      [common/info-box {:variant :warning
                        :content warn}])
    [:div {:class (<class common-styles/flex-row-space-between)}
     [buttons/button-secondary {:on-click on-close}
      (tr [:buttons :cancel])]
     [buttons/button-primary {:on-click (e! cost-items-controller/->UnlockForEdits on-close)}
      (tr [:asset :confirm-unlock-for-edits])]]]])

(defn- boq-version-statusline [e! {:keys [latest-change version version-history]}]
  (r/with-let [dialog (r/atom nil)
               set-dialog! #(reset! dialog %)]
    (let [{:keys [user timestamp] :as chg} latest-change
          locked? (asset-model/locked? version)
          action (if locked?
                   :asset/unlock-for-edits
                   :asset/lock-version)
          last-locked-version (if locked?
                                version
                                (first version-history))]
      [:div {:class (<class common-styles/flex-row)
             :style {:background-color theme-colors/gray-lightest
                     :width "100%"}}

       (cond
         (asset-model/locked? version)
         [:<>
          (tr-enum (:boq-version/type version))
          " v." (:boq-version/number version)]
         chg
         [:<>
          [:b (tr [:common :last-modified]) ": "]
          (fmt/date-time timestamp)])

       (when chg
         [common/popper-tooltip
          {:variant :no-icon
           :multi [(when last-locked-version
                     {:title (tr-enum (:boq-version/type last-locked-version))
                      :body (str " v." (:boq-version/number last-locked-version))})
                   (when last-locked-version
                     {:title (tr [:fields :boq-version/explanation])
                      :body (:boq-version/explanation last-locked-version)})
                   {:title (tr [:common :last-modified])
                    :body [:<>
                           (fmt/date-time timestamp)
                           [:br]
                           (user-model/user-name user)]}]}
          [icons/alert-error-outline]])

       ;; Save or unlock button
       [when-authorized action nil
        [buttons/button-secondary
         {:disabled (some? @dialog)
          :size :small
          :on-click (r/partial set-dialog! action)}
         (tr [:asset (case action
                       :asset/unlock-for-edits :unlock-for-edits
                       :asset/lock-version :save-boq-version)])]]

       (when-let [dialog @dialog]
         (case dialog
           :asset/unlock-for-edits
           [unlock-for-edits-dialog {:e! e!
                                     :on-close (r/partial set-dialog! nil)
                                     :version version}]

           :asset/lock-version
           [save-boq-version-dialog
            {:e! e!
             :on-close (r/partial set-dialog! nil)}
            last-locked-version]))])))


(defn cost-items-page-structure
  [{:keys [e! app state left-panel-action hierarchy right-panel]} main-content]
  (r/with-let [export-dialog-open? (r/atom false)
               open-export-dialog! #(reset! export-dialog-open? true)
               close-export-dialog! #(reset! export-dialog-open? false)]
    [:<>
     (when @export-dialog-open?
       [export-boq-dialog e! app close-export-dialog!])

     (let [{:keys [asset-type-library]} app
           {:keys [cost-items project version] :as page-state} state]
       [context/provide :rotl (asset-type-library/rotl-map asset-type-library)
        [context/provide :locked? (asset-model/locked? version)
         [project-view/project-full-page-structure
          {:e! e!
           :app app
           :project project
           :export-menu-items
           [{:id "export-boq"
             :label (tr [:asset :export-boq])
             :icon [icons/file-download]
             :on-click open-export-dialog!}]
           :left-panel
           [:div {:style {:overflow-y :scroll}}
            [cost-items-navigation-select e! app]
            left-panel-action
            [cost-item-hierarchy
             (merge hierarchy
                    {:e! e!
                     :app app
                     :add? (= :new-cost-item (:page app))
                     :project project
                     :cost-items cost-items})]]
           :content-margin "0 0"
           :main
           [:<>
            [boq-version-statusline e! page-state]
            main-content]
           :right-panel right-panel
           :right-panel-padding 0}]]])]))

(defn filter-breadcrumbs [{:keys [root-label atl query filter-kw page]}]
  (when-let [hierarchy (some->> filter-kw
                                (asset-type-library/type-hierarchy atl))]
    [breadcrumbs/breadcrumbs
     (into
      [{:link [url/Link {:page page :query {:filter nil}}
               root-label]
        :title root-label}]
      (for [h hierarchy
            :let [title (label h)]]
        {:link [url/Link {:page page
                          :query (merge query {:filter (str (:db/ident h))})}
                title]
         :title title}))]))

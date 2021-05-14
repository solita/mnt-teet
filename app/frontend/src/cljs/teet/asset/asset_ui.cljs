(ns teet.asset.asset-ui
  "Common asset related UI components"
  (:require [clojure.string :as str]
            [teet.ui.context :as context]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.ui.typography :as typography]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.select :as select]
            [teet.util.string :as string]
            [teet.localization :as localization :refer [tr]]))

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
          :format-result format-fg-and-fc
          :show-empty-selection? true
          :clear-value [nil nil]
          :query (fn [text]
                   #(vec
                     (for [fg fgroups
                           fc (:fclass/_fgroup fg)
                           :let [result [fg fc]]
                           :when (string/contains-words? (format-fg-and-fc result) text)]

                       result)))}]]])))

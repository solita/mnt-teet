(ns teet.ui.tabs
  (:require [teet.ui.material-ui :refer [Tabs Tab]]
            [teet.common.common-controller :as common-controller]))

(defn tabs [{:keys [e! selected-tab class]} & tabs]
  (let [tabs (map-indexed
              (fn [i tab]
                (assoc tab ::index i))
              tabs)
        index->tab (into {}
                         (map (juxt ::index identity))
                         tabs)
        value->tab (into {}
                         (map (juxt :value identity))
                         tabs)]
    [Tabs {:value (::index (value->tab selected-tab))
           :textColor "primary"
           :class class
           :on-change (fn [_ v]
                        (let [tab (index->tab v)]
                          (e! (common-controller/->SetQueryParam :tab (:value tab)))))}
     (doall
      (for [{:keys [value label]} tabs]
        (Tab {:key value
              :disable-ripple true
              :label label})))]))

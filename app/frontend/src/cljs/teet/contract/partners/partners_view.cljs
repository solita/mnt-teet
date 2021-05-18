(ns teet.contract.partners.partners-view)
;; Targeted from routes.edn will be located in route /contracts-partners
(defn partners-listing-view
  [e! {route :route :as app}]
  (r/with-let [default-filtering-value {:shortcut :all}]
    [Grid {:container true}
     [Grid {:item true
            :xs 12}
      [:div {}
       [:h1 "MENU"]]]
     [:h1 "CONTRACTS LIStING"]]))
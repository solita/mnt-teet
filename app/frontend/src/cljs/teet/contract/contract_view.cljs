(ns teet.contract.contract-view)


(defn contract-page
  [e! app contract]
  [:div
   [:h1 "contract page"]
   [:span (pr-str contract)]])

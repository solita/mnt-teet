(ns teet.workflow.workflow-view
  (:require [teet.workflow.workflow-controller :as workflow-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.format :as format]))


(defn workflow-page
  "Main workflow page"
  [e! {:workflow/keys [phases] :as workflow}]
  [:<>
   (for [phase phases]
     ^{:key (:db/id phase)}
     [itemlist/ProgressList
      {:title (:phase/name phase) :subtitle (some-> phase :phase/due-date format/date)}
      (for [{:task/keys [status name] :as task} (:phase/tasks phase)]
        ^{:key name}
        {:status status
         :name name})])
   [:div (pr-str workflow)]])

(defn workflow-page-and-title [e! {params :params :as app}]
  (let [id (params :workflow)
        workflow (get-in app [:workflow id])]
    {:title (:workflow/name workflow)
     :page [workflow-page e! workflow]}))

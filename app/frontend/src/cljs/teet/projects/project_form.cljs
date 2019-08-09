(ns teet.projects.project-form
  "Form to create a new project or edit an existing one."
  (:require [teet.ui.form :as form]
            [teet.ui.common :as common]
            [teet.localization :refer [tr tr-key]]))

(defn project-form [e! project]
  [form/form {:name->label (comp (tr-key [:fields "project"]
                                         [:fields :common])
                                 name)
              :update! #(e! :D)}
   [(form/group
     "Yleiset"
     {:type :string
      :name :name})]
   project])

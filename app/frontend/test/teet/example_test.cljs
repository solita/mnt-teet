(ns teet.example-test
  (:require-macros [drtest.core :refer [define-drtest]])
  (:require drtest.core
            [drtest.step :refer [step]]
            [reagent.core :as r]))

(define-drtest my-component-test
  {;; :screenshots? true ... TODO update drtest so that these can be run
   ;; without clj-chrome-devtools
   :initial-context {:app (r/atom {})}}
   (step :render "Render component"
         :component (fn [{app :app}]
                      [:button#test-button {:on-click #(swap! app assoc :test true)}]))

   (step :click "Click the doit button"
         :selector "#test-button")

   ^{:drtest.step/label "Check results in app state"}
   (fn [{app :app}]
     (= true (:test @app))))

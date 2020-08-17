(ns teet.example-test
  (:require [teet.drtest :as drt :include-macros true]))

(drt/define-drtest example-component-test
  {:initial-context {:app (drt/atom {})}}
   (drt/step :render "Render component"
             :component (fn [{app :app}]
                          [:button#test-button {:on-click #(swap! app assoc :test true)}]))

   (drt/step :click "Click the doit button"
             :selector "#test-button")

   ^{:drtest.step/label "Check results in app state"}
   (fn [{app :app}]
     (= true (:test @app))))

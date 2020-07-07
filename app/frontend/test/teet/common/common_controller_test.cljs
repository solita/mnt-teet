(ns teet.common.common-controller-test
  (:require [teet.app-state :as app-state]
            [teet.common.common-controller :refer [feature-enabled? when-feature]]
            [teet.drtest :as drt :include-macros true]))

(defn test-view [_e! _app]
  (println "foo2")
  (fn [_e! _app]
    [:div
    (when-feature :test-feature
      [:span#feature "It's-a-me, feature!"])]))

(defn initialize-enabled-features [features-set]
  {:drtest.step/label "Wait for test initialization"
   :drtest.step/type :wait-promise
   :promise (js/Promise.
             (fn [ok err]
               (swap! app-state/app assoc :enabled-features features-set)
               (ok)))})

(drt/define-drtest component-shown-if-feature-enabled
  {:initial-context {:app (drt/atom {:is :unused})}}

  (initialize-enabled-features #{:test-feature :other-feature})

  (drt/step :tuck-render "Render component with feature"
            :component test-view)

  (drt/step :expect "Expect to find span with feature"
            :selector "span[id=\"feature\"]"))

(drt/define-drtest component-not-shown-if-feature-not-enabled
  {:initial-context {:app (drt/atom {:is :unused})}}

  (initialize-enabled-features #{})

  (drt/step :tuck-render "Render component with feature"
            :component test-view)

  (drt/step :expect-no "Expect not to find span with feature"
            :selector "span[id=\"feature\"]"))

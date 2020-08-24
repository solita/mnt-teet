(ns teet.common.common-controller-test
  (:require [teet.app-state :as app-state]
            [teet.common.common-controller :refer [feature-enabled? when-feature]]
            [teet.drtest :as drt :include-macros true]))

(defn test-view [_e! _app]
  (fn [_e! _app]
    [:div
    (when-feature :test-feature
      [:span#feature "It's-a-me, feature!"])]))


(drt/define-drtest component-shown-if-feature-enabled
  {:initial-context {:app (drt/atom {:is :unused
                                     :enabled-features #{:test-feature :other-feature}})}}

  (drt/step :tuck-render "Render component with feature"
            :component test-view)

  (drt/step :expect "Expect to find span with feature"
            :selector "span[id=\"feature\"]"))

(drt/define-drtest component-not-shown-if-feature-not-enabled
  {:initial-context {:app (drt/atom {:is :unused
                                     :enabled-features #{}})}}

  (drt/step :tuck-render "Render component with feature"
            :component test-view)

  (drt/step :expect-no "Expect not to find span with feature"
            :selector "span[id=\"feature\"]"))

(ns teet.example-tuck-test
  (:require [teet.project.project-controller]
            [teet.drtest :as drt :include-macros true]
            [tuck.core :as t]))

(defrecord MakeBackendRequest [])

(extend-protocol t/Event
  MakeBackendRequest
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :workflow/create-project-workflow
           :payload :foo
           :result-event #(println %)})))

(defn test-view [e! _]
  [:button#tuck-test-button {:on-click #(e! (->MakeBackendRequest))}])

(drt/define-drtest example-tuck-test
  {;; :screenshots? true ... TODO update drtest so that these can be run
   ;; without clj-chrome-devtools
   :initial-context {:app (drt/atom {})}}
  (drt/step :render "Render with tuck"
            :component (fn [{app :app}]
                         [t/tuck app test-view]))

  (drt/step :click "Click the Tuck test button"
            :selector "#tuck-test-button")

  (drt/step :wait-request ":workflow/create-project-workflow called"
            :predicate #(= (:command %)
                           :workflow/create-project-workflow)))

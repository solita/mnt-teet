(ns teet.ui.unauthorized
  (:require [teet.ui.typography :refer [Heading1 Paragraph]]
            [teet.localization :refer [tr]]))

(defn restricted-path
  [e!]
  [:div {:style {:display :flex
                 :align-items :center
                 :flex-direction :column
                 :margin-top "5rem"}}
   [Heading1 (tr [:common :forbidden])]
   [Paragraph (tr [:common :forbidden-explanation])]])

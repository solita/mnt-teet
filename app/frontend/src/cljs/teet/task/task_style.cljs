(ns teet.task.task-style)

(defn task-status-container-style
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :border-bottom "solid 1px"
   :border-color teet.theme.theme-colors/gray-light
   :padding-bottom "1rem"
   :margin-bottom "1rem"})

(defn task-status-style
  []
  {:flex-basis "30%"})

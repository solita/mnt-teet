(ns teet.ui.file-upload
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]
            [teet.ui.material-ui :refer [Button]]))

(defn ->vector [file-list]
  (mapv #(.item file-list %)
        (range (.-length file-list))))

(defn- file-vector [e]
  (-> e .-target .-files ->vector))

(defn file-from-drop [e]
  (let [dt (.-dataTransfer e)]
    (-> e
        .-dataTransfer
        .-files
        ->vector)))

(defn- on-drag-over [event]
  (.stopPropagation event)
  (.preventDefault event))

(defn- on-drag-enter-f [f event]
  (.stopPropagation event)
  (.preventDefault event)
  (f))

(defn- on-drag-leave-f [f event]
  (.stopPropagation event)
  (.preventDefault event)
  (f))

(defn- on-drop-f [f event]
  (.stopPropagation event)
  (.preventDefault event)
  (f (file-from-drop event)))

(defn FileUpload
  "Note! Use one of the predefined file upload components, such as
  FileUploadButton instead of using this directly."
  [{:keys [id on-drop on-drag-enter on-drag-leave]
    :or {on-drag-enter identity
         on-drag-leave identity}}
   & children]
  (into [:label
         {:htmlFor id
          :on-drop #(on-drop-f on-drop %)
          :on-drag-over on-drag-over
          :on-drag-enter #(on-drag-enter-f on-drag-enter %)
          :on-drag-leave #(on-drag-leave-f on-drag-leave %)}
         [:input {:style {:display "none"}
                  :id id
                  :multiple true
                  :droppable "true"
                  :type "file"
                  :on-change #(on-drop (file-vector %))}]]
        children))

(defn FileUploadButton [{:keys [id on-drop] :as props}]
  (let [hover (r/atom 0)]
    (fn [{:keys [id on-drop] :as props}]
      [FileUpload {:id "test-id"
                   :on-drop on-drop
                   :on-drag-enter #(swap! hover inc)
                   :on-drag-leave #(swap! hover dec)}
       [Button {:component "span"
                :color (if (pos? @hover) :secondary :primary)
                :variant :outlined
                :raised "true"}
        "Drag or click to upload"]])))

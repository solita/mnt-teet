(ns teet.ui.file-upload
  (:require [reagent.core :as r]
            [taoensso.timbre :as log]
            [teet.ui.material-ui :refer [Button]]
            [tuck.core :as t]))

(defrecord TestFileUpload [files])

(extend-protocol t/Event
  TestFileUpload
  (process-event [{files :files} app]
    (log/info (map #(.-name %) files))
    app))

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

(defn- on-drop [e! tuck-event event]
  (.stopPropagation event)
  (.preventDefault event)
  (e! (tuck-event (file-from-drop event))))

(defn FileUpload [e! {:keys [id event]}]
  [:<>
   [:label {:htmlFor id
            :on-drop (partial on-drop e! event)
            :on-drag-over on-drag-over}
    [:input {:style {:display "none"}
             :id id
             :multiple true
             :droppable "true"
             :type "file"
             :on-change #(e! (event (file-vector %)))}]
    [Button {:component "span"
             :color :primary
             :variant :outlined
             :raised "true"}
     "Upload"]]])

(ns teet.ui.drag
  "Global drag&drop listener."
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]))

(defn- page-overlay []
  {:position :fixed
   :z-index 1500

   ;; Opaque background
   :background-color (theme-colors/primary-alpha 0.25)

   ;; Cover the whole page
   :height "100%"
   :width "100%"
   :left 0
   :top 0})

(defn- drop-zone-style []
  {:position :fixed
   :z-index 1501
   ;; Opaque background
   :background-color (theme-colors/primary-alpha 0.5)

   ;; Border
   :border-style :dashed
   :border-width "5px"
   :border-radius "10px"
   :border-color theme-colors/secondary

   ;; Text
   :color theme-colors/white
   :font-size "2rem"
   :text-align :center})

(def ^:const empty-drag-state {:dragging 0 :drop-zones {}})

(defonce drag-state (r/atom empty-drag-state))

(defn- on-drag-enter [_e]
  (swap! drag-state update :dragging inc))


(defn- on-drag-leave [_e]
  (swap! drag-state update :dragging dec))

(defn- cancel [evt]
  (.stopPropagation evt)
  (.preventDefault evt))

(defn- on-drop-f [on-drop]
  (fn [event]
    (cancel event)
    (swap! drag-state assoc :dragging 0)
    (on-drop event)))

(defn register-drop-zone! [{:keys [element-id label on-drop] :as dz}]
  (assert (string? element-id) "Element id must be a string")
  (assert (string? label) "Drop zone must have a label")
  (assert (fn? on-drop) "on-drop handler must be a function")
  (let [id (str (gensym "DROPZONE"))]
    (swap! drag-state update :drop-zones assoc id dz)
    #(swap! drag-state update :drop-zones dissoc id)))

(defn suppress-drop-zones!
  "Suppress all current drop zones. Meant to be called in mount/unmount
  of modals that need to disable other drop zones.

  Returns function that reinstates the suppressed drop zones."
  []

  (let [state @drag-state]
    (reset! drag-state empty-drag-state)
    #(reset! drag-state state)))

(defn drop-zone [{:keys [element-id label on-drop]}]
  (let [pos (-> element-id
                js/document.getElementById
                .getClientRects
                (aget 0))]

    [:div.drop-zone {:style {:left (aget pos "left")
                             :top (aget pos "top")
                             :width (aget pos "width")
                             :height (aget pos "height")}
                     :class (<class drop-zone-style)
                     :on-drop (on-drop-f on-drop)
                     :on-drag-over cancel}
     label]))

(defn drag-handler
  "Global drag&drop handler. Should be rendered as part
  of root view."
  [_e!]
  (set! (.-ondragenter js/window) on-drag-enter)
  (set! (.-ondragleave js/window) on-drag-leave)
  (fn [e!]
    (let [{:keys [dragging drop-zones]} @drag-state
          dragging? (pos? dragging)]
      [:<>
       (when dragging?
         [:div.drag-overlay {:class (<class page-overlay)
                             :on-drop (on-drop-f
                                       (e! snackbar-controller/->OpenSnackBar
                                           (tr [:drag :no-drop-zone])
                                           :error))
                             :on-drag-over cancel}
          (doall
           (for [[id dz] drop-zones]
             ^{:key id}
             [drop-zone dz]))])])))

(defn ->vector [file-list]
  (mapv (fn [i]
          {:file-object (.item file-list i)})
        (range (.-length file-list))))

(defn dropped-files
  "Get files from drop event. Call from on-drop handler."
  [e]
  (-> e
      .-dataTransfer
      .-files
      ->vector))

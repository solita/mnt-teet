(ns teet.ui.split-pane
  "React split pane"
  (:require
   [reagent.core :as r]
   react-split-pane))

(def ^:private SplitPane (r/adapt-react-class (aget react-split-pane "default")))
(def ^:private Pane (r/adapt-react-class (aget react-split-pane "Pane")))

(def ^:private inject-resizer-style
  (delay
    (.appendChild
     js/document.head
     (doto (js/document.createElement "style")
       (.setAttribute "type" "text/css")
       (.appendChild (js/document.createTextNode
                      ".Resizer {
  background: #000;
  opacity: 0.2;
  z-index: 1;
  -moz-box-sizing: border-box;
  -webkit-box-sizing: border-box;
  box-sizing: border-box;
  -moz-background-clip: padding;
  -webkit-background-clip: padding;
  background-clip: padding-box;
}

.Resizer:hover {
  -webkit-transition: all 2s ease;
  transition: all 2s ease;
}

.Resizer.horizontal {
  height: 11px;
  margin: -5px 0;
  border-top: 5px solid rgba(255, 255, 255, 0);
  border-bottom: 5px solid rgba(255, 255, 255, 0);
  cursor: row-resize;
  width: 100%;
}

.Resizer.horizontal:hover {
  border-top: 5px solid rgba(0, 0, 0, 0.5);
  border-bottom: 5px solid rgba(0, 0, 0, 0.5);
}

.Resizer.vertical {
  width: 11px;
  margin: 0 -5px;
  border-left: 5px solid rgba(255, 255, 255, 0);
  border-right: 5px solid rgba(255, 255, 255, 0);
  cursor: col-resize;
}

.Resizer.vertical:hover {
  border-left: 5px solid rgba(0, 0, 0, 0.5);
  border-right: 5px solid rgba(0, 0, 0, 0.5);
}
.Resizer.disabled {
  cursor: not-allowed;
}
.Resizer.disabled:hover {
  border-color: transparent;
}"))))))

(defn split-pane [opts & children]
  @inject-resizer-style
  (into [SplitPane opts] children))

(defn pane [opts & children]
  (into [Pane opts] children))

(defn vertical-split-pane
  "Create vertical split pane that wraps children with hidden x overflow."
  [opts & children]
  [split-pane (merge opts {:split "vertical"})
   (map-indexed
    (fn [i child]
      ^{:key (str "pane" i)}
      [:div {:style {:overflow-x "hidden" :height "100%"}}
       child]) children)])

(defn horizontal-split-pane
  "Create horizontal split pane that wraps children with hidden y overflow."
  [opts & children]
  [split-pane (merge opts {:split "vertical"})
   (map-indexed
    (fn [i child]
      ^{:key (str "pane" i)}
      [:div {:style {:overflow-y "hidden"}}
       child]) children)])

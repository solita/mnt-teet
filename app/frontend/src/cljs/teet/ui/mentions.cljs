(ns teet.ui.mentions
  "A mentions input"
  (:require [herb.core :as herb :refer [<class]]
            [teet.user.user-model :as user-model]
            [teet.ui.common :as common]
            [reagent.core :as r]
            react-mentions
            [clojure.set :as set]
            [teet.common.common-styles :as common-styles]
            [teet.ui.select :as select]))

(def Mention (r/adapt-react-class (aget react-mentions "Mention")))
(def MentionsInput (r/adapt-react-class (aget react-mentions "MentionsInput")))

(defn mentions-input
  [_]
  (let [input-ref (atom nil)
        caret (atom nil)
        old-mentions (atom nil)]
    (r/create-class
      {:component-did-update
       (fn [_]
         (let [inp @input-ref
               c @caret]
           (when (and inp c)
             (set! (.-selectioStart inp) c)
             (set! (.-selectionEnd inp) c))))

       :reagent-render
       (fn [{:keys [e! value on-change _error _read-only? label id required]}]
         [:label.mention {:for id}
          [:span {:class (<class common-styles/label-text-style)}
           label (when required
                   [common/required-astrix])]
          [MentionsInput {:value value
                          :id id
                          :on-focus (fn [_]
                                      (reset! caret (.-selectionStart @input-ref)))
                          :on-change (fn [e new-value new-plain-text-value new-mentions]
                                       (let [old-mention-ids (into #{} (map #(aget % "id")) @old-mentions)
                                             new-mention-ids (into #{} (map #(aget % "id")) new-mentions)
                                             added-mention (first (set/difference new-mention-ids old-mention-ids))
                                             removed-mention (first (set/difference old-mention-ids new-mention-ids))]
                                         (reset! caret
                                                 (cond
                                                   ;; Mention added: move caret after it
                                                   added-mention
                                                   (some #(when (= (aget % "id") added-mention)
                                                            (+ (aget % "plainTextIndex")
                                                               (count (aget % "display")))) new-mentions)

                                                   ;; Mention removed: move caret at it's start index
                                                   removed-mention
                                                   (some #(when (= (aget % "id") removed-mention)
                                                            (aget % "plainTextIndex")) @old-mentions)

                                                   ;; No mentions changed, keep caret at current position
                                                   :else
                                                   (.-selectionStart @input-ref)))
                                         (reset! old-mentions new-mentions))
                                       (on-change e)
                                       (when (not= (count new-plain-text-value)
                                                   @caret)
                                         ;; Flush forces re-render did-update call
                                         ;; Otherwise there would be situations in which the change is fired twice
                                         ;; without a call to did-update
                                         (r/flush)))
                          :input-ref #(reset! input-ref %)
                          :class-name "comment-textarea"}
           [Mention {:trigger "@"
                     :display-transform (fn [_ display]
                                          (str "@" display))
                     :class-name "mentions__mention"
                     :data (fn [search callback]
                             (e! (select/->CompleteUser
                                   search
                                   (fn [users]
                                     (callback
                                       (into-array
                                         (for [u users]
                                           #js {:display (user-model/user-name u)
                                                :id (str (:db/id u))})))))))}]]])})))

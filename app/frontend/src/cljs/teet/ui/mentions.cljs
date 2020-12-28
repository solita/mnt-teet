(ns teet.ui.mentions
  "A mentions input"
  (:require [herb.core :as herb :refer [<class]]
            [teet.user.user-model :as user-model]
            [teet.ui.common :as common]
            [reagent.core :as r]
            ["react" :as react]
            react-mentions
            [clojure.set :as set]
            [teet.common.common-styles :as common-styles]
            [teet.ui.select :as select]
            [teet.ui.util :as uu]))

(def Mention (r/adapt-react-class (aget react-mentions "Mention")))
(def MentionsInput (r/adapt-react-class (aget react-mentions "MentionsInput")))


(defn- set-caret! [inp pos]
  (set! (.-selectionStart inp) pos)
  (set! (.-selectionEnd inp) pos))

(defn mentions-input*
  [{:keys [e! value on-change _error _read-only? label id required]}]
  (let [input-ref (uu/use-state-atom nil)
        caret (uu/use-state-atom nil)
        old-mentions (uu/use-state-atom nil)]

    ;; After initial render, set focus to end
    (uu/use-effect #(do
                      (when-let [inp @input-ref]
                        (set-caret! inp (count (.-value inp))))
                      uu/no-cleanup)
                   @input-ref)

    ;; After each change, keep caret in correct place
    (uu/use-effect #(let [inp @input-ref
                          c @caret]
                      (when (and inp c)
                        (set-caret! inp c))
                      uu/no-cleanup)
                   @input-ref @caret)

    [:label.mention {:for id}
     [:span {:class (<class common-styles/label-text-style)}
      label (when required
              [common/required-astrix])]
     [MentionsInput
      {:value value
       :id id
       :on-change (fn [e _new-value new-plain-text-value new-mentions]
                    (let [new-mention-ids (into #{} (map #(aget % "id")) new-mentions)
                          current-old-mentions @old-mentions
                          old-mention-ids (if (nil? current-old-mentions)
                                            ;; First change, old mentions not initialized yet
                                            ;; Use the new mentions
                                            new-mention-ids

                                            ;; Use old mentions from previous edit
                                            (into #{} (map #(aget % "id")) current-old-mentions))
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
                                        :id (str (:user/id u))})))))))}]]]))

(defn mentions-input [args]
  [:f> mentions-input* args])

(ns teet.meeting.meeting-pdf
  "Generate meeting PDF as hiccup formatted XSL-FO"
  (:require
    [teet.meeting.meeting-db :as meeting-db]
    [teet.meeting.meeting-commands :as meeting-commands]
    [teet.environment :as environment]
    [teet.localization :refer [with-language tr tr-enum]])
  (:import (com.vladsch.flexmark.parser Parser)
           (com.vladsch.flexmark.util.ast Document)
           (com.vladsch.flexmark.ast
            ;; Import node types for rendering
            Paragraph BulletList OrderedList Heading Text
            StrongEmphasis Emphasis)))

(declare render-md)

(def default-layout-config
  {;; A4 portrait width/height
   :width "21cm"
   :height "29.7cm"

   ;; 1cm margins
   :margin-top "1cm" :margin-bottom "1cm"
   :margin-left "1cm" :margin-right "1cm"

   :body {:margin-top "1cm"}
   :header {:extent "1cm"}
   :footer {:extent "1cm"}})

(def link-look-and-feel
  {:font-size "16px"
   :border-top-style "solid"
   :border-top-width "1"
   :border-top-color "#D2D3D8"
   :padding-top 8
   :padding-bottom 10})

(def date-format
  (doto (java.text.SimpleDateFormat. "MM.dd.yyyy" )
    (.setTimeZone (java.util.TimeZone/getTimeZone "Europe/Tallinn"))))

(def time-format
  (doto (java.text.SimpleDateFormat. "HH:mm" )
    (.setTimeZone (java.util.TimeZone/getTimeZone "Europe/Tallinn"))))

(defn format-date
  "Format date in human readable locale specific format, eg. dd.MM.yyyy"
  [date]
  (when date
    (.format date-format date)))

(defn format-time
  "Format time with minute resolution"
  [date]
  (when date
    (.format time-format date)))

(defn- meeting-time
  "Format meeting begin, end time, date"
  [meeting]
  (str (format-date (:meeting/start meeting))
       " " (format-time (:meeting/start meeting)) " - "
       (format-time (:meeting/end meeting))))

(defn- decision-list-item
  "Return the agenda topic descition list item"
  [decision topic]
  (let [title (str topic " " (tr+ [:fields :meeting.decision/body]) " #" (:meeting.decision/number decision))
        decision-text (render-md (:meeting.decision/body decision))]
    [:fo:block
     [:fo:block {:font-size "24px" :font-weight "400"} title]
     [:fo:block {:font-size "14px" :font-weight "400" :space-after "40"} decision-text]]))

(defmulti link-list-item (fn [link] (:link/type link)))

(defmethod link-list-item :file [link]
  [:fo:block
   [:fo:block link-look-and-feel
    (str "Appendix " (get-in link [:link/to :file/original-name]))]])

(defmethod link-list-item :task [link]
  [:fo:block
   [:fo:block link-look-and-feel
    (str "Linked task " (get-in link [:link/to :task/type :db/ident]))]])

(defmethod link-list-item :estate [link]
  [:fo:block
   [:fo:block link-look-and-feel
    (str "Linked estate " (get-in link [:link/external-id]))]])

(defmethod link-list-item :cadastral-unit [link]
  [:fo:block
   [:fo:block link-look-and-feel
    (str "Linked cadastral unit "(get-in link [:link/external-id]))]])

(defn- list-of-topics
  "Return list of agenda topics"
  [topics]
  (when (seq topics)
    [:fo:list-block {:space-after "40"}
     (map (fn [topic]
            [:fo:list-item
             [:fo:list-item-label [:fo:block]]
             [:fo:list-item-body
              [:fo:block
               [:fo:block {:font-size "28px" :font-weight "400" :space-after "2"}
                (:meeting.agenda/topic topic)]
               [:fo:block {:font-size "14px" :font-weight "700" :space-after "24"}
                [:fo:inline (:user/given-name (:meeting.agenda/responsible topic)) " "
                 (:user/family-name (:meeting.agenda/responsible topic))]]
               [:fo:block {:font-size "16px"} (render-md (:meeting.agenda/body topic))]
               [:fo:block {:space-after "40"} (map link-list-item (:link/_from topic))]
               [:fo:block {:space-after "40"} (map #(decision-list-item % (:meeting.agenda/topic topic))
                                                   (:meeting.agenda/decisions topic))]]]]) topics)]))

(defn- table-2-columns
  "Returns 2 columns FO table"
  [{:keys [left-width right-width
           left-header right-header
           left-content right-content
           fonts]}]
  (let [ {{header-font-size :font-size
           header-font-weight :font-weight
           header-font-style :font-style} :header-font
          {rows-font-size :font-size
           rows-font-weight :font-weight
           rows-font-style :font-style} :rows-font} fonts]
  [:fo:table {}
   [:fo:table-column {:column-width left-width}]
   [:fo:table-column {:column-width right-width}]
   [:fo:table-header
    [:fo:table-row
     [:fo:table-cell
      [:fo:block {:font-size header-font-size :font-weight header-font-weight :font-style header-font-style} left-header]]
     [:fo:table-cell
      [:fo:block {:font-size header-font-size :font-weight header-font-weight :font-style header-font-style} right-header]]]]
   [:fo:table-body
    [:fo:table-row
     [:fo:table-cell
      [:fo:block {:font-size rows-font-size :font-weight rows-font-weight :font-style rows-font-style} left-content]]
     [:fo:table-cell
      [:fo:block {:font-size rows-font-size :font-weight rows-font-weight :font-style rows-font-style} right-content]]]]]))

(defn- tr+
  "Give both translations"
  [key]
  (str (with-language :et (tr key)) " / "
       (with-language :en (tr key))))

(defn- decision-text
  "Return decision text depending on approved or rejected"
  [db-ident]
  (case db-ident
    :review.decision/approved
    "Approved"
    :review.decision/rejected
    "Rejected"
    "Unknown"))

(defn- reviews
  "Returns review information"
  [review-of]
  (let [review (first review-of) reviewer (:review/reviewer review)
        reviewer-family-name (:user/family-name reviewer)
        reviewer-given-name (:user/given-name reviewer)
        decision (:review/decision review)
        comment (:review/comment review)]
    [:fo:block {:font-style "normal" :font-size "20px"}
     (tr+ [:meeting :approvals])
     [:fo:block {:font-style "italic" :font-size "16px"}
      "Reviewer: " (str reviewer-family-name " " reviewer-given-name " "
                        (decision-text (:db/ident decision)) " " comment " Date: " (:meta/created-at review))]]))

(defn- participants
  "Returns the participants - attendees or absentees"
  [meeting is-absentee?]
  (for [{user       :participation/participant
         role       :participation/role
         is-absent? :participation/absent?} (:participation/_in meeting)
        :when (if is-absentee?
                is-absent?
                (not is-absent?))]
    [:fo:block
     [:fo:inline {:font-weight 900} (:user/family-name user) " " (:user/given-name user)]
     [:fo:inline ", " (with-language :en (tr-enum role))]]))

(defn- fetch-project-id
  "Find project id by meeting"
  [meeting]
  (get-in meeting [:activity/_meetings 0 :thk.lifecycle/_activities 0
                   :thk.project/_lifecycles 0 :thk.project/id]))
(defn- meeting-title
  "Format meeting titels"
  [meeting]
  [:fo:block {:font-family  "Helvetica, Arial, sans-serif"
              :font-size    "32px" :font-weight "300" :line-height "48px"
              :font-style "normal" :space-before "5" :space-after "5"}
   (:meeting/title meeting)])

(defn meeting-pdf
  ([db meeting-id]
   (meeting-pdf db meeting-id default-layout-config))
  ([db meeting-id {:keys [body header footer] :as layout}]
   (let [meeting (meeting-db/export-meeting db meeting-id)
         project-id (fetch-project-id meeting)
         base-url (or (environment/config-value :base-url) "")
         url (meeting-commands/meeting-link
               db base-url meeting [:thk.project/id project-id])]
     [:fo:root {:xmlns:fo  "http://www.w3.org/1999/XSL/Format"
                :xmlns:svg "http://www.w3.org/2000/svg"}
      [:fo:layout-master-set
       [:fo:simple-page-master {:master-name   "first"
                                :page-height   (:height layout)
                                :page-width    (:width layout)
                                :margin-top    (:margin-top layout)
                                :margin-bottom (:margin-bottom layout)
                                :margin-left   (:margin-left layout)
                                :margin-right  (:margin-right layout)}
        [:fo:region-body {:region-name "xsl-region-body"
                          :margin-top  (:margin-top body)}]
        [:fo:region-before {:region-name "xsl-region-before"
                            :extent      (:extent header)}]
        [:fo:region-after {:region-name "xsl-region-after"
                           :extent      (:extent footer)}]]]
      [:fo:page-sequence {:master-reference "first"}
       [:fo:flow {:flow-name "xsl-region-body"}
        (meeting-title meeting)
        [:fo:block {:space-after "10"}
         (table-2-columns {:left-width    "40%" :left-header (tr+ [:fields :meeting/date-and-time])
                           :right-width   "60%" :right-header (tr+ [:fields :meeting/location])
                           :left-content  [:fo:block (meeting-time meeting)]
                           :right-content [:fo:block (:meeting/location meeting)]
                           :fonts { :header-font {:font-size "12px" :font-weight "700" :font-style "normal"}
                                   :rows-font {:font-size "14px" :font-weight "400" :font-style "normal"}}})]
        [:fo:block {:space-after "54"}
         (table-2-columns {:left-width   "45%" :left-header (tr+ [:meeting :participants-title])
                          :right-width   "55%" :right-header (tr+ [:meeting :absentees-title])
                          :left-content  [:fo:block (participants meeting false)]
                          :right-content [:fo:block (participants meeting true)]
                          :fonts {:header-font {:font-size "20px" :font-weight "400" :font-style "normal"}
                                  :rows-font {:font-size "12px" :font-weight "700" :font-style "normal"}}})]
        [:fo:block
         (list-of-topics (:meeting/agenda meeting))]
        [:fo:block
         (reviews (:review/_of meeting))]
        [:fo:block
         (str (tr+ [:meeting :link-to-original]) " ")]
        [:fo:block
         [:fo:basic-link
          {:external-destination url}
          [:fo:inline {:text-decoration "underline" :color "blue"} url]]]
        [:fo:block (tr+ [:meeting :pdf-created-by])
         [:fo:block "User comes here"]]]]])))

(defn- md-children [node]
  (-> node .getChildren .iterator iterator-seq))

(defmulti md->xsl-fo class)

(defn- render-children [node]
  (for [c (md-children node)]
    (md->xsl-fo c)))

(defmethod md->xsl-fo Document [root]
  [:fo:block
   (render-children root)])

(defmethod md->xsl-fo Paragraph [c]
  [:fo:block
   (render-children c)])


(defmethod md->xsl-fo BulletList [ul]
  [:fo:list-block
   (for [item (md-children ul)]
     [:fo:list-item
      [:fo:list-item-label {:end-indent "label-end()"}
       [:fo:block [:fo:inline {:font-family "Symbol"} "\u2022"]]]
      [:fo:list-item-body {:start-indent "body-start()"}
       [:fo:block
        (for [c (md-children item)]
          (md->xsl-fo c))]]])])

(defmethod md->xsl-fo OrderedList [ol]
  [:fo:list-block
   (map-indexed
    (fn [i item]
      [:fo:list-item
       [:fo:list-item-label {:end-indent "label-end()"}
        [:fo:block [:fo:inline (str (inc i))]]]
       [:fo:list-item-body {:start-indent "body-start()"}
        [:fo:block
         (for [c (md-children item)]
           (md->xsl-fo c))]]])
    (md-children ol))])

(defmethod md->xsl-fo Text [t]
  (str (.getChars t)))

(defmethod md->xsl-fo StrongEmphasis [t]
  [:fo:inline {:font-weight 900}
   (render-children t)])

(defmethod md->xsl-fo Emphasis [t]
  [:fo:inline {:font-style "italic"}
   (render-children t)])

(defmethod md->xsl-fo Heading [h]
  [:fo:block {:font-size (case (.getLevel h)
                           1 "24pt"
                           2 "18pt"
                           3 "16pt")}
   (render-children h)])

(defn- parse-md [str]
  (-> (Parser/builder) .build (.parse str)))

(defn- render-md
  "Parse and render markdown as xsl-fo"
  [md-formatted-text]
  (md->xsl-fo (parse-md md-formatted-text)))

(comment
  (def md "hello **everyone**\n\n​\n\nlets\n\n​\n\n1. do\n2. some things\n3. here\n\n## level two header\n\nsome more content _italic also_\n")
  (def doc (parse-md md))
  (md->xsl-fo doc))

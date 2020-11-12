(ns teet.meeting.meeting-pdf
  "Generate meeting PDF as hiccup formatted XSL-FO"
  (:import (com.vladsch.flexmark.parser Parser)
           (com.vladsch.flexmark.util.ast NodeIterator Document
                                          ContentNode Block)
           (com.vladsch.flexmark.ast
            ;; Import node types for rendering
            Paragraph BulletList OrderedList Heading)))

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

(defn meeting-pdf
  ([db meeting-id]
   (meeting-pdf db meeting-id default-layout-config))
  ([db meeting-id {:keys [body header footer] :as layout}]
   [:fo:root {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
              :xmlns:svg "http://www.w3.org/2000/svg"}
    [:fo:layout-master-set
     [:fo:simple-page-master {:master-name "first"
                              :page-height (:height layout)
                              :page-width (:width layout)
                              :margin-top (:margin-top layout)
                              :margin-bottom (:margin-bottom layout)
                              :margin-left (:margin-left layout)
                              :margin-right (:margin-right layout)}
      [:fo:region-body {:region-name "xsl-region-body"
                        :margin-top (:margin-top body)}]
      [:fo:region-before {:region-name "xsl-region-before"
                          :extent (:extent header)}]
      [:fo:region-after {:region-name "xsl-region-after"
                         :extent (:extent footer)}]]]

    [:fo:page-sequence {:master-reference "first"}
     [:fo:flow {:flow-name "xsl-region-body"}
      [:fo:block {:font-size "16pt"}
       "IMPLEMENT ME"]]]]))

(defn- md-children [node]
  (-> node .getChildren .iterator iterator-seq))

(defmulti md->xsl-fo class)

(defmethod md->xsl-fo Document [root]
  [:fo:block {:class "md-document"}
   (for [c (md-children root)]
     (md->xsl-fo c))])

(defmethod md->xsl-fo Paragraph [c]
  [:fo:block
   (for [i (range 0 (.getLineCount c))
         :let [line (.getLineChars c i)]]
     (str line))])


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

(defmethod md->xsl-fo Heading [h]
  [:fo:block (pr-str h)])

(defn- parse-md [str]
  (-> (Parser/builder) .build (.parse str)))

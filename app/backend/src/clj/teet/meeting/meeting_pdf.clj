(ns teet.meeting.meeting-pdf
  "Generate meeting PDF as hiccup formatted XSL-FO")

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
    [:fo:layet-master-set
     [:fo:simple-page-master {:master-name "first"
                              :page-height (:height layout)
                              :page-iwdth (:width layout)
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

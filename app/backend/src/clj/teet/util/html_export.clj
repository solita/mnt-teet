(ns teet.util.html-export
  (:require [teet.localization :refer [tr with-language]]
            [teet.util.collection :as cu]
            [hiccup.core :as h]))

(def copy-to-clipboard-script (str "function copyToClipboard() {"
                                   "var e = document.getElementById('export');"
                                   "var s = window.getSelection();"
                                   "var r = document.createRange();"
                                   "r.selectNodeContents(e);"
                                   "s.removeAllRanges();"
                                   "s.addRange(r);"
                                   "document.execCommand('Copy');"
                                   "}"))

(defn html-export-helper
  [{:keys [title content]}]
  (h/html
    (cu/eager
      [:html
       [:head
        [:title title]
        [:script
         copy-to-clipboard-script]]
       [:body
        [:button {:style "float: right;"
                  :onclick "copyToClipboard()"}
         (tr [:buttons :copy-to-clipboard])]
        content]])))

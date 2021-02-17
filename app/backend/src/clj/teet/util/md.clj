(ns teet.util.md
  (:require
   [clojure.string :as str]
   [hiccup.core :as h])
  (:import (com.vladsch.flexmark.parser Parser Parser$ParserExtension)
           (com.vladsch.flexmark.util.data DataHolder)
           (com.vladsch.flexmark.util.sequence BasedSequence)
           (com.vladsch.flexmark.util.ast Document Node DelimitedNode)
           (com.vladsch.flexmark.parser.delimiter DelimiterProcessor)
           (com.vladsch.flexmark.ast
             ;; Import node types for rendering
             Paragraph BulletList OrderedList Heading Text
             StrongEmphasis Emphasis)))

(declare render-md)

(defn- md-children [node]
  (-> node .getChildren .iterator iterator-seq))

(defn- node-type [node]
  (let [s (str node)]
    (cond
      ;; Check our extension classes
      (str/starts-with? s "#<Underline")
      "Underline"

      ;; Otherwise use Java class of node
      :else
      (class node))))

(defmulti md->xsl-fo node-type)
(defmulti md->html node-type)

(defn- render-children [node]
  (for [c (md-children node)]
    (md->xsl-fo c)))

(defn- render-children-html [node]
  (for [c (md-children node)]
    (md->html c)))

(defmethod md->xsl-fo Document [root]
  [:fo:block
   (render-children root)])

(defmethod md->html Document [root]
  [:span
   (render-children-html root)])

(defmethod md->xsl-fo Paragraph [c]
  [:fo:block
   (render-children c)])

(defmethod md->html Paragraph [c]
  [:p (render-children-html c)])

(defmethod md->xsl-fo BulletList [ul]
  [:fo:list-block
   (for [item (md-children ul)]
     [:fo:list-item
      [:fo:list-item-label { :font-family "Symbol" :end-indent "label-end()"}
       [:fo:block [:fo:inline "\u2022"]]]
      [:fo:list-item-body {:start-indent "body-start()"}
       [:fo:block
        (for [c (md-children item)]
          (md->xsl-fo c))]]])])

(defmethod md->html BulletList [ul]
  [:ul
   (for [item (md-children ul)]
     [:li (render-children-html item)])])

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

(defmethod md->html OrderedList [ol]
  [:ol
   (for [item (md-children ol)]
     [:li (render-children-html item)])])

(defmethod md->xsl-fo Text [t]
  (str (.getChars t)))

(defmethod md->html Text [t] (h/h (str (.getChars t))))

(defmethod md->xsl-fo StrongEmphasis [t]
  [:fo:inline {:font-weight 900}
   (render-children t)])

(defmethod md->html StrongEmphasis [t]
  [:b (render-children-html t)])

(defmethod md->xsl-fo Emphasis [t]
  [:fo:inline {:font-style "italic"}
   (render-children t)])

(defmethod md->html Emphasis [t]
  [:i (render-children-html t)])

(defmethod md->xsl-fo "Underline" [t]
  [:fo:inline {:text-decoration "underline"}
   (render-children t)])

(defmethod md->html "Underline" [t]
  [:ins (render-children-html t)])

(defmethod md->xsl-fo Heading [h]
  [:fo:block {:font-size (case (.getLevel h)
                           1 "24pt"
                           2 "18pt"
                           3 "16pt")}
   (render-children h)])

(defn create-underline-node [opening-marker text closing-marker]
  (let [state (atom {:opening-marker opening-marker
                     :text text
                     :closing-marker closing-marker})]
    (proxy [Node DelimitedNode]
           [(.baseSubSequence opening-marker
                              (.getStartOffset opening-marker)
                              (.getEndOffset closing-marker))]
      (getSegments []
        (let [{:keys [opening-marker text closing-marker]} @state]
          (into-array BasedSequence [opening-marker text closing-marker])))
      (getOpeningMarker [] (:opening-marker @state))
      (getText [] (:text @state))
      (getClosingMarker [] (:closing-marker @state))
      (setText [text] (swap! state assoc :text text))
      (toString [] (str "#<Underline " (pr-str @state) ">")))))

(defn- underline-delimiter-processor []
  (reify DelimiterProcessor
    (getOpeningCharacter [this] \+)
    (getClosingCharacter [this] \+)
    (getMinLength [this] 2)
    (getDelimiterUse [this opener closer]
      (if (and (>= (.length opener) 2)
               (>= (.length closer) 2))
        2
        0))
    (canBeOpener [this before after leftFlanking rightFlanking beforeIsPunctuation
                  afterIsPunctuation beforeIsWhitespace afterIsWhiteSpace]
      leftFlanking)
    (canBeCloser [this before after leftFlanking rightFlanking beforeIsPunctuation
                  afterIsPunctuation beforeIsWhitespace afterIsWhiteSpace]
      rightFlanking)
    (unmatchedDelimiterNode [this inlineParser delimiter] nil)
    (skipNonOpenerCloser [this] false)
    (process [this opener closer delimitersUsed]
      (.moveNodesBetweenDelimitersTo
        opener
        (create-underline-node (.getTailChars opener delimitersUsed)
                               BasedSequence/NULL
                               (.getLeadChars closer delimitersUsed))
        closer))))

(defn- underline-extension []
  (reify Parser$ParserExtension
    (parserOptions [this options])
    (extend [this parser-builder]
      (.customDelimiterProcessor parser-builder (underline-delimiter-processor)))))

(defn- parse-md [str]
  (let [extensions [(underline-extension)]]
    (-> (Parser/builder
          (reify DataHolder
            (get [this key]
              (when (= key Parser/EXTENSIONS)
                extensions))
            (getAll [this]
              {Parser/EXTENSIONS extensions})))
        .build (.parse str))))

(defn render-md
  "Parse and render markdown as xsl-fo"
  [md-formatted-text]
  (md->xsl-fo (parse-md md-formatted-text)))

(defn render-md-html
  [md-formatted-text]
  (md->html (parse-md md-formatted-text)))

(comment
  (def md "hello **everyone**\n\n​\n\nlets\n\n​\n\n1. do\n2. some things\n3. here\n\n## level two header\n\nsome more content _italic also_\n")
  (def doc (parse-md md))
  (md->xsl-fo doc))

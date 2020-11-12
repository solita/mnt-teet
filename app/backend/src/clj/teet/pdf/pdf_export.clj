(ns teet.pdf.pdf-export
  "PDF export functionality using XSL-FO."
  (:require [hiccup.core :as h])
  (:import (javax.xml.transform.sax SAXResult)
           (javax.xml.transform.stream StreamSource)
           (javax.xml.transform TransformerFactory)
           (org.apache.fop.apps FopFactory FopFactoryBuilder MimeConstants)
           (java.io StringReader OutputStream)))

(set! *warn-on-reflection* true)

(def ^:private fop-factory
  (delay
    (.build (FopFactoryBuilder. (java.net.URI. "file:///tmp/teet")))))


(defn hiccup->pdf [xsl-fo-content ^OutputStream ostream]
  ;; sanitize?
  (let [xml (h/html xsl-fo-content)
        ^FopFactory factory @fop-factory
        fop (.newFop factory MimeConstants/MIME_PDF ostream)
        xform (.newTransformer (TransformerFactory/newInstance))
        src (StreamSource. (StringReader. xml))
        res (SAXResult. (.getDefaultHandler fop))]
    (.transform xform src res)))

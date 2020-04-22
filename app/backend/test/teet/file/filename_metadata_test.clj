(ns teet.file.filename-metadata-test
  (:require [teet.file.filename-metadata :refer [filename->metadata
                                                 metadata->filename]]
            [clojure.test :refer [deftest is]]))

(def test-filename "MA12345_EP_TL-123_RR_666_my_fine_drawing.dwg")

(deftest filename-metadata-roundtrip
  (let [filename1 test-filename
        metadata (filename->metadata filename1)
        filename2 (metadata->filename metadata)]
    (is (= filename1 filename2)
        "filename generated from parsed metadata is the same")))

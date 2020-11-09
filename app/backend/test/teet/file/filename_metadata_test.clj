(ns teet.file.filename-metadata-test
  (:require [teet.file.filename-metadata :refer [filename->metadata
                                                 metadata->filename]]
            [clojure.test :refer [deftest is]]
            [teet.util.string :as string]))


(def example-filenames
  ["MA14658_EP_TL_PT_00_Projekt tingimused.pdf"
   "MA14658_EP_TL_GL_00_Aruanne.pdf"
   "MA14658_EP_TL_GL_00_3-1_puurauk2.pdf"
   "MA14658_EP_TL_GL_00_3-2_puurauk3.pdf"
   "MA14658_EP_TL_CL_01_3-1_mahud.xls"
   "MA14658_EP_TL_CL_02_3-2_mahud.pdf"
   "MA14658_EP_TL_TL_01_1-01_AP-Vinso.dwg"
   "MA14658_EP_TL_TL_01_1-02_AP-Leevi.dwg"
   "MA14658_EP_TL_TS_02_1_Uskuna.dwg"
   "MA14658_EP_TL_KY_00_1_Uskuna.pdf"
   "MA14658_EP_TL_AA_00_0-101_esimesed100m_plaan.pdf"
   "MA14658_EP_TL_AA_00_0-102_keskmine jupp.pdf"
   "MA14658_EP_TL_AA_00_0-103_esimesed 100 m-pikiprofiil.pdf"
   "MA14658_EH_TL_CL_00_kululoend.xls"
   "MA14658_EH_TL_TL_00_1-01_teostusjoonis.dwg"])


(deftest filename-metadata-roundtrip
  (doseq [filename1 example-filenames
          :let [metadata (filename->metadata filename1)
                filename2 (metadata->filename metadata)]]
    (is (= (string/strip-leading-zeroes filename1)
           (string/strip-leading-zeroes filename2))
        "filename generated from parsed metadata is the same")))

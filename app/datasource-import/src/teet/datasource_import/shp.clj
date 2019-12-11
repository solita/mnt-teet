(ns teet.datasource-import.shp
  "Read ESRI Shapefile (.shp) features"
  (:import (org.geotools.data.shapefile ShapefileDataStore)
           (org.geotools.data.simple SimpleFeatureIterator)
           (org.geotools.feature.simple SimpleFeatureImpl)
           (org.opengis.feature.simple SimpleFeature)
           (org.locationtech.jts.io WKBWriter))
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(defn shp-ds [file]
  (-> file io/as-url
      ShapefileDataStore.))


(def ds (shp-ds (io/file "/Users/tatuta/projects/mnt-teet/app/backend/Ehitusgeoloogia_uuringualad_shp/Ehitusgeoloogia_uuringualad_shp.shp")))

(defn- to-iterator [^SimpleFeatureIterator it]
  (reify
    java.util.Iterator
    (hasNext [_] (.hasNext it))
    (next [_] (.next it))

    java.lang.AutoCloseable
    (close [_] (.close it))))

(def ignore-attributes #{"the_geom"})

(let [wkb-writer (WKBWriter.)]
  (defn ->wkb [geometry]
    (WKBWriter/bytesToHex
     (.write wkb-writer geometry))))

(defn feature-data [^SimpleFeatureImpl f]
  (let [attr-names (set/difference
                    (into #{}
                          (map (comp str #(.getName %)))
                          (.getProperties f))
                    ignore-attributes)]
    {:geometry (->wkb (doto (.getDefaultGeometry f)
                        (.setSRID 3301)))
     :attributes (into {}
                       (map (fn [attr-name]
                              [attr-name
                               (->> attr-name
                                    (.getProperty f)
                                    .getValue)]))
                       attr-names)}))

(defn features [shp]
  (->> shp
       .getFeatureSource
       .getFeatures
       .features
       to-iterator
       iterator-seq
       (map feature-data)))

(defn read-features-from-path
  "Takes a path that contains a shapefile (.shp and related files).
  Returns a lazy sequence of parsed features."
  [path]
  (let [shp-file (->> path .toFile .listFiles
                      seq (filter #(str/ends-with? (.getName %) ".shp"))
                      first)]
    (when-not shp-file
      (throw (ex-info "Path doesn't contain an ESRI Shapefile. No file with .shp suffix found."
                      {:path path})))
    (println "Reading features from ESRI Shapefile:" (.getAbsolutePath shp-file))
    (features (shp-ds shp-file))))

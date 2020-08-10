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

(def ignore-attributes #{"the_geom"})

(let [wkb-writer (WKBWriter. 2 true)]
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
                              [(keyword attr-name)
                               (->> attr-name
                                    (.getProperty f)
                                    .getValue)]))
                       attr-names)}))

(defn- feature-iterator-seq [^ShapefileDataStore ds ^SimpleFeatureIterator it]
  (if (.hasNext it)
    (lazy-seq
     (cons (.next it)
           (feature-iterator-seq ds it)))
    (do
      (println "No more features, closing feature iterator.")
      (.close it)
      (.dispose ds)
      nil)))

(defn features [shp]
  (->> shp
       .getFeatureSource
       .getFeatures
       .features
       (feature-iterator-seq shp)
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

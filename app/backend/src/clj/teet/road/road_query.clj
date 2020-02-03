(ns teet.road.road-query
  "Query road geometry from Teeregister"
  (:require [org.httpkit.client :as client]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as z]
            [hiccup.core :as hiccup]
            [teet.util.geo :as geo]))

(defn- ogc-filter [content]
  (hiccup/html
   [:Filter {:xmlns "http://www.opengis.net/ogc"}
    content]))

(defn- query-by-road-and-carriageway [road carriageway]
  (ogc-filter
   [:And
    [:PropertyIsEqualTo
     [:PropertyName "tee_number"]
     [:Literal road]]
    [:PropertyIsEqualTo
     [:PropertyName "soidutee_nr"]
     [:Literal carriageway]]]))

(defn- query-by-coordinate [[x y] distance-m]
  (ogc-filter
   [:DWithin
    [:PropertyName "msGeometry"]
    [:Point {:xmlns "http://www.opengis.net/gml"}
     [:coordinates {:decimal "." :cs "," :ts " "}
      (str y "," x)]]
    [:Distance {:units "meter"} distance-m]]))

(defn- ->int [txt]
  (Long/parseLong txt))

(defn- read-geometry-linestring [elt]
  (-> elt
      (z/xml1-> :gml:LineString :gml:posList z/text)
      (str/split #" ")
      (as->
          pos
          (map #(Double/parseDouble %) pos)
        (partition 2 pos)
        (map reverse pos))))

(defn- read-feature-collection [feature-collection]
  (sort-by
   :sequence-nr
   (z/xml->
    feature-collection
    :gml:featureMember
    :ms:teeosa
    (fn [road-part]
      (let [prop (fn [name parse]
                   (z/xml1-> road-part name z/text parse))
            start-m (prop :ms:m_aadress ->int)
            length (prop :ms:pikkus ->int)]
        {:road (prop :ms:tee_number ->int)
         :carriageway (prop :ms:soidutee_nr ->int)
         :name (prop :ms:nimi str)
         :sequence-nr (prop :ms:jrknr ->int)
         :start-m start-m
         :end-m (+ start-m length)
         :length length
         :geometry (z/xml-> road-part :ms:msGeometry read-geometry-linestring)})))))

(defn extract-part
  "Extract a linestring geometry for a part of the road.
  Returns sequence of points comprising the linestring.

  The start-m and end-m are meter values within the stated start/end of the road part.
  Because the road geometries are not completely accurate, the actual geometry may have
  a different length from the part's stated start/end meters. The requested start/end
  meters will be scaled to the actual geometry length.

  The returned geometry actual length may be different from requested."
  [{part-start-m :start-m
    part-end-m :end-m
    part-geometry :geometry
    :as road-part} start-m end-m]

  (when (or (<= part-start-m start-m part-end-m)
            (<= part-start-m end-m part-end-m))
    (let [length-factor (/ (geo/line-string-length part-geometry)
                           (- part-end-m part-start-m))
          start-m (+ part-start-m
                     (* length-factor (- start-m part-start-m)))
          end-m (+ part-start-m
                   (* length-factor (- end-m part-start-m)))]
      (loop [traveled part-start-m
             previous-point nil
             [p & points] part-geometry
             acc []]
        (if (or (nil? p)
                (> traveled end-m))
          ;; We have traveled the whole geometry, return the result
          acc

          (let [distance-to-next-point (if previous-point
                                         (geo/distance p previous-point)
                                         0)
                new-traveled (+ traveled distance-to-next-point)]
            (cond
              ;; If we have traveled to the start-m and not yet
              ;; past end-m, add this point
              (<= start-m traveled end-m)
              (if (> new-traveled end-m)
                ;; Traveling past the desired range, interpolate end point
                (conj acc (geo/interpolate-point previous-point p
                                                 (/ (- end-m traveled)
                                                    (geo/distance previous-point p))))
                ;; Not yet done traveling, gather this point
                (recur new-traveled p points (conj acc p)))

              ;; The next point would exceed start-m, interpolate start point
              (> new-traveled start-m)
              (let [start-point (geo/interpolate-point previous-point
                                                       p
                                                       (/ (- start-m traveled)
                                                          (- new-traveled traveled)))]
                (recur start-m start-point
                       (concat [p] points)
                       [start-point]))

              ;; Not yet reached start-m, continue traveling
              :else
              (recur new-traveled p points acc))))))))

(defn extract-road-geometry [road-parts-for-carriageway start-m end-m]
  (mapcat #(extract-part % start-m end-m) road-parts-for-carriageway))

(defn dump-road-part-lengths [road-part]
  (loop [len 0
         previous-point nil
         [p & points] (:geometry road-part)]
    (if-not p
      nil
      (do
        (println (:start-m road-part) "+" len "m = " (+ (:start-m road-part) len))
        (recur (+ len (if previous-point
                        (geo/distance previous-point p)
                        0))
               p points))))
  (println "part " (:start-m road-part) (:end-m road-part)))


(defn- wfs-request [wfs-url query-params]
  (let [{:keys [error body]}
        @(client/get wfs-url
                     {:connect-timeout 10000
                      :query-params (merge
                                     {:SERVICE "WFS"
                                      :REQUEST "GetFeature"
                                      :VERSION "1.1.0"
                                      :TYPENAME "ms:teeosa"
                                      :SRSNAME "urn:ogc:def:crs:EPSG::3301"}
                                     query-params)
                      :as :stream})]
    (if error
      (throw (ex-info "Unable to fetch road parts from WFS"
                      {:status 500
                       :error :wfs-request-failed
                       :wfs-url wfs-url
                       :query-params query-params}
                      error))
      (-> body xml/parse zip/xml-zip
          read-feature-collection))))

(defn fetch-road-parts [{wfs-url :wfs-url} road-nr carriageway-nr]
  (wfs-request wfs-url {:FILTER (query-by-road-and-carriageway road-nr carriageway-nr)}))

(defn fetch-road-parts-by-coordinate [{wfs-url :wfs-url} coordinate distance-m]
  (wfs-request wfs-url {:FILTER (query-by-coordinate coordinate distance-m)}))

(defn fetch-road-geometry
  "Fetch road geometry for the given road, carriageway and start/end range.
  Returns a line-string (sequence of coordinates).

  Queries Maanteeamet WFS service specified in `wfs-url` option.

  If `cache-atom` is given, it is used to cache fetched road parts
  and used in subsequent calls for the same road/carriagway. This will
  reduce query load for the WFS server when fetching multiple road geometries."
  [{:keys [wfs-url cache-atom]} road-nr carriageway-nr start-m end-m]
  (let [parts (or (and cache-atom
                       (get @cache-atom [road-nr carriageway-nr]))
                  (fetch-road-parts {:wfs-url wfs-url} road-nr carriageway-nr))]
    (when cache-atom
      (swap! cache-atom assoc [road-nr carriageway-nr] parts))
    (extract-road-geometry parts start-m end-m)))

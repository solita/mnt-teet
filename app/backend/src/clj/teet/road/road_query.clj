(ns teet.road.road-query
  "Query road geometry and road objects from Teeregister"
  (:require [org.httpkit.client :as client]
            [clojure.string :as str]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as z]
            [hiccup.core :as hiccup]
            [teet.util.geo :as geo]
            [teet.util.collection :as cu]
            [teet.log :as log]))

(defn unexceptional-xml-parse [input]
  (try
    (xml/parse input)
    (catch Exception _
      ;; caller has to log error about input resulting in nil parse
      nil)))

(defn- ogc-filter [content]
  (hiccup/html
   [:Filter {:xmlns "http://www.opengis.net/ogc"
             :xmlns:gml "http://www.opengis.net/gml"}
    content]))

(defn- raw [xml]
  (reify hiccup.compiler/HtmlRenderer
    (render-html [_] xml)))

(defn- query-by-values [values]
  (into [:And]
        (map (fn [[key value]]
               (let [[op value] (if (vector? value)
                                  [(case (first value)
                                     :> :PropertyIsGreaterThan
                                     :>= :PropertyIsGreaterThanOrEqualTo
                                     :< :PropertyIsLessThan
                                     :<= :PropertyIsLessThanOrEqualTo
                                     := :PropertyIsEqualTo)
                                   (second value)]
                                  [:PropertyIsEqualTo value])]
                 [op
                  [:PropertyName (if (keyword? key)
                                   (name key) key)]
                  [:Literal value]])))
        values))

(defn- query-by-road-and-carriageway [road carriageway]
  (ogc-filter
   (query-by-values {:tee_number road :soidutee_nr carriageway})))

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

(defn- parse-road-part [teeosa]
  (z/xml1->
   teeosa :ms:teeosa
   (fn [road-part]
     (let [prop (fn [name parse]
                  (z/xml1-> road-part name z/text parse))
           start-m (prop :ms:m_aadress ->int)
           length (prop :ms:pikkus ->int)]
       {:oid (prop :ms:oid identity)
        :road (prop :ms:tee_number ->int)
        :carriageway (prop :ms:soidutee_nr ->int)
        :name (prop :ms:nimi str)
        :sequence-nr (prop :ms:jrknr ->int)
        :start-m start-m
        :end-m (+ start-m length)
        :length length
        :geometry (z/xml-> road-part :ms:msGeometry read-geometry-linestring)}))))

(defn- read-feature-collection
  [feature-collection typename parse-feature]
  (sort-by
   :sequence-nr
   (z/xml->
    feature-collection
    :gml:featureMember
    (keyword typename)
    parse-feature)))

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
    :as _road-part} start-m end-m]

  (cond
    ;; The whole part is included, return geometry as is
    (and (<= start-m part-start-m)
         (>= end-m part-end-m))
    part-geometry

    ;; The wanted range starts or ends in this part
    (or (<= part-start-m start-m part-end-m)
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

(defn- cached [cache-atom cache-key fetch-fn]
  (if-let [cached-value (and cache-atom
                             (get @cache-atom cache-key))]
    cached-value
    (let [fetched-value (fetch-fn cache-key)]
      (when cache-atom
        (swap! cache-atom assoc cache-key fetched-value))
      fetched-value)))

(defn- wfs-request [{:keys [cache-atom wfs-url]} query-params]
  (cached
   cache-atom
   query-params
   (fn [{::keys [parse-feature] :as  query-params}]
     (let [query-params (merge {:SERVICE "WFS"
                                :REQUEST "GetFeature"
                                :VERSION "1.1.0"
                                :TYPENAME "ms:teeosa"
                                :SRSNAME "urn:ogc:def:crs:EPSG::3301"}
                               (dissoc query-params ::parse-feature))
           _ (log/info "WFS request: url" wfs-url "query-params" query-params)
           {:keys [error body] :as response}
           @(client/get wfs-url
                        {:connect-timeout 10000
                         :query-params query-params
                         :as :stream})]
       (if error
         (throw (ex-info "Unable to fetch road parts from WFS"
                         {:status 500
                          :error :wfs-request-failed
                          :wfs-url wfs-url
                          :query-params query-params}
                         error))
         (let [zx (-> body unexceptional-xml-parse zip/xml-zip)]
           (if-not zx
             (do
               (log/error "couldn't parse XML from WFS:" zx)
               (throw (ex-info "WFS response error"
                         {:status 500
                          :error :wfs-request-failed
                          :wfs-url wfs-url
                          :query-params query-params
                          :response response})))
             (read-feature-collection zx
                                      (:TYPENAME query-params)
                                      (or parse-feature parse-road-part)))))))))

(defn fetch-road-parts [config road-nr carriageway-nr]
  (wfs-request config {:FILTER (query-by-road-and-carriageway road-nr carriageway-nr)}))

(defn fetch-road-parts-by-coordinate [config coordinate distance-m]
  (wfs-request config {:FILTER (query-by-coordinate coordinate distance-m)}))

(defn fetch-road-info [config road-nr carriageway-nr]
  (let [parts (fetch-road-parts config road-nr carriageway-nr)]
    {:name (:name (first parts))
     :total-length (reduce + (map :length parts))
     :start-m (reduce min (map :start-m parts))
     :end-m (reduce max (map :end-m parts))}))

(defn fetch-road-geometry
  "Fetch road geometry for the given road, carriageway and start/end range.
  Returns a line-string (sequence of coordinates).

  Queries Maanteeamet WFS service specified by `:wfs-url` in config.

  If config `:cache-atom` is given, it is used to cache requests responses
  and used in subsequent calls with the same query parameters. This will
  reduce query load for the WFS server when fetching multiple road geometries."
  [config road-nr carriageway-nr start-m end-m]
  (extract-road-geometry (fetch-road-parts config road-nr carriageway-nr)
                         start-m end-m))

(defn- flipc [[c1 c2]]
  [c2 c1])

(defn- split-positions [pos-list-str]
  (partition 2
             (map #(Double/parseDouble %)
                  (str/split pos-list-str #" "))))

(defn- gml->geojson [node]
  (or (z/xml1-> node :gml:LineString :gml:posList z/text
                (fn [positions]
                  {:type "LineString"
                   :coordinates
                   (mapv flipc (split-positions positions))}))
      (z/xml1-> node :gml:Point :gml:pos z/text
                (fn [pos]
                  {:type "Point"
                   :coordinates (flipc (first (split-positions pos)))}))
      (do
        (log/warn "Unknown geometry from Teeregister: "
                  (zip/node node))
        nil)))

(comment #(with-out-str
            (-> % zip/node :content first
                xml/emit-element)))
(defn- parse-feature
  "Parse any WFS feature member"
  [f]
  (into {:geometry (z/xml1-> f :ms:msGeometry gml->geojson)}
        (keep (fn [child]
                (let [{:keys [tag content]} (zip/node child)
                      tag-name (name tag)]
                  ;; Teeregister fields, take the content value
                  (when (and (not= tag :ms:msGeometry)
                             (str/starts-with? tag-name "ms:"))
                    [tag (str/join " " content)]))))
        (dz/children f)))

(defn fetch-intersecting-objects-of-type
  "Fetch road objects for given typename intersecting the given area."
  [config typename gml-geometry]
  (wfs-request config
               {:TYPENAME typename
                :FILTER (ogc-filter [:Intersects
                                     [:PropertyName "msGeometry"]
                                     (raw gml-geometry)])
                ::parse-feature parse-feature}))

(defn fetch-objects-of-type-for-road
  "Fetch road object for given typename that are within the road part"
  [config typename {:keys [road carriageway sequence-nr start-m end-m]}]
  (log/info "Fetch " typename " objects, road: " road ", carriageway: " carriageway ", sequence-nr: " sequence-nr ", meters: " start-m " - " end-m)
  (wfs-request config
               {:TYPENAME typename
                :FILTER (ogc-filter
                         [:And
                          (query-by-values {:tee_number road
                                            :soidutee_nr carriageway})
                          [:Or
                           ;; In the first part
                           (query-by-values {:algteeosa sequence-nr
                                             :algteeosa_meeter [:<= start-m]})

                           ;; Between first and second part
                           (query-by-values {:algteeosa [:> sequence-nr]
                                             :loppteeosa [:< sequence-nr]})

                           ;; In the last part
                           (query-by-values {:loppteeosa sequence-nr
                                             :loppteeosa_meeter [:>= end-m]})]])
                ::parse-feature parse-feature}))


(def road-object-types
  ["ms:n_bussipeatus"
   "ms:n_kandur"
   "ms:n_liiklussolm"
   "ms:n_mahasoit"
   "ms:n_rdtyl"
   "ms:n_ristmik"
   "ms:n_parkla"
   "ms:n_seade"
   "ms:n_truup"
   "ms:n_katlai"
   "ms:n_piire"
   "ms:n_loomatoke"
   "ms:n_lumekaitse_hekk"
   "ms:n_murasein"
   "ms:n_valgustus"
   "ms:n_sild"
   "ms:n_vork"
   "ms:n_ylek"
   "ms:n_kiiruspiirang"
   "ms:n_defekt"
   "ms:n_hoole"
   "ms:n_kandevoime"
   "ms:n_kate"
   "ms:n_katend"
   "ms:n_kergliiklustee"
   "ms:n_kruusateede_seisukord"
   "ms:n_kulmakerked"
   "ms:n_liigitus"
   "ms:n_liiklussagedus"
   "ms:n_pindam"
   "ms:n_rvteed"
   "ms:n_rooprm"
   "ms:n_roobas"
   "ms:n_tasas"
   "ms:n_tekstuur"
   "ms:n_tolm"
   "ms:n_tahispostid"])

(defn fetch-all-intersecting-objects
  "Returns all intersecting objects.
  Returns a map containing the typename and the objects of that type."
  [config gml-geometry]
  (into {}
        (filter
         (comp seq second)
         (pmap (fn [type]
                 [type (fetch-intersecting-objects-of-type config type gml-geometry)])
               road-object-types))))

(defn fetch-all-objects-for-road
  [config road-part]
  (into {}
        (filter
         (comp seq second)
         (pmap (fn [type]
                 [type (fetch-objects-of-type-for-road config type road-part)])
               road-object-types))))

(defn- fetch-capabilities [url service]
  (log/info "fetch" service "capabilities from" url)
  (let [{:keys [status body] :as response}
        @(client/get url
                     {:as :stream
                      :query-params {:version "1.3.0"
                                     :request "GetCapabilities"
                                     :service service}})]
    (if (not= status 200)
      (throw (ex-info "Unable to fetch WMS capabilities"
                      {:url url
                       :service service
                       :response response}))
      (if-let [parsed (unexceptional-xml-parse body)]
        (zip/xml-zip parsed)
        (do
          (log/error "couldn't parse WMS capabilities xml:" body)
          (throw (ex-info "Unable parse capability query response from WFS"
                          {:url url
                           :service service
                           :response response})))))))

(defn fetch-wms-capabilities
  "Fetch WMS capabilities XML. Returns XML zipper."
  [{wms-url :wms-url}]
  (fetch-capabilities wms-url "WMS"))

(defn fetch-wfs-capabilities
  "Fetch WFS capabilities XML. Returns XML zipper."
  [{wfs-url :wfs-url}]
  (fetch-capabilities wfs-url "WFS"))

(defn- text-of [node child-element]
  (z/xml1-> node child-element z/text))

(defn- parse-wms-layer [node]
  (let [name (text-of node :Name)
        title (text-of node :Title)]
    {:name name
     :title title
     :queryable? (= "1" (z/xml1-> node (z/attr :queryable)))
     :legend-url (z/xml1-> node :Style :LegendURL :OnlineResource (z/attr :xlink:href))
     :layers (z/xml-> node dz/children :Layer parse-wms-layer)}))

(defn fetch-wms-layers [config]
  (let [c (fetch-wms-capabilities config)]
    (z/xml-> c :Capability :Layer parse-wms-layer)))

(defn- parse-wfs-feature-type [ft]
  {:name (text-of ft :Name)
   :title (text-of ft :Title)})

(defn fetch-wfs-feature-types [config]
  (let [c (fetch-wfs-capabilities config)]
    (z/xml-> c :FeatureTypeList :FeatureType parse-wfs-feature-type)))

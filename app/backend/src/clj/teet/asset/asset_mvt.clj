(ns teet.asset.asset-mvt
  "Generate MVT tiles from asset start/end lines."
  (:import (vector_tile VectorTile$Tile VectorTile$Tile$Layer
                        VectorTile$Tile$Feature
                        VectorTile$Tile$GeomType)))

(defn- zz [n]
  (let [n (int n)]
    (int
     (bit-xor (bit-shift-left n 1) (bit-shift-right n 31)))))

(defn- cmd-with-len [cmd len]
  (int
   (bit-or (bit-shift-left (int len) 3)
           (int cmd))))

(defn- move-to [feature-builder [x y]]
  (-> feature-builder
      (.addGeometry (bit-or (bit-shift-left 1 3) 1))
      (.addGeometry (int x))
      (.addGeometry (int y))))

(defn- line-to [feature-builder [x y]]
  (-> feature-builder
      (.addGeometry 2)
      (.addGeometry (int x))
      (.addGeometry (int y))))

(defn cmds [builder & lists]
  (loop [b builder
         [c & cs] (apply concat lists)]
    (if-not c
      b
      (recur (.addGeometry b c)
             cs))))

(defn- asset-feature [{:location/keys [start-point end-point]}]
  (-> (VectorTile$Tile$Feature/newBuilder)
      (.setType VectorTile$Tile$GeomType/LINESTRING)
      (cmds
       ;; moveto repeat 1
       [(cmd-with-len 1 1)]
       (map zz start-point)

       ;; lineto repeat 1
       [(cmd-with-len 2 1)]
       (map zz end-point))
      ;; FIXME: interpolate asset coords to tile extent 0 - 4096
      ))

(defn- add-assets [layer-builder assets]
  (loop [b layer-builder
         [a & assets] assets]
    (if-not a
      b
      (recur (.addFeatures b (asset-feature a)) assets))))

(defn- layer [name assets]
  (-> (VectorTile$Tile$Layer/newBuilder)
      (.setExtent 4096)
      (.setVersion 2)
      (.setName name)
      (add-assets assets)
      .build))

(defn mvt [layer-name assets]
  (-> (VectorTile$Tile/newBuilder)
      (.addLayers (layer layer-name assets))
      .build
      .toByteArray))

(ns teet.road.road-query-test
  (:require [teet.road.road-query :as road-query]
            [teet.util.geo :as geo]
            [clojure.test :refer [deftest testing is]]))

(def simple-road-part {:start-m 100 :end-m 200 :geometry [[0 0] [100 0]]})

(deftest extract-part-interpolation
  (testing "end point is interpolated"
    (let [extracted (road-query/extract-part simple-road-part 100 180)]
      (is (= 80.0 (geo/line-string-length extracted)))
      (is (= [80.0 0.0] (last extracted)))))

  (testing "start point is interpolated"
    (let [extracted (road-query/extract-part simple-road-part 125 200)]
      (is (= 75.0 (geo/line-string-length extracted)))
      (is (= [25.0 0.0] (first extracted)))))


  (testing "both start and end point are interpolated"
    (let [extracted (road-query/extract-part simple-road-part 130 170)]
      (is (= 40.0 (geo/line-string-length extracted)))
      (is (= [30.0 0.0] (first extracted)))
      (is (= [70.0 0.0] (last extracted))))))

;; Part that should be 1000m long, but is actually 1131.37m long
(def inaccurate-length-part {:start-m 500
                             :end-m 1500
                             :geometry [[0 0]
                                        [400 400]
                                        [800 800]]})
(deftest length-factor
  (let [extract #(road-query/extract-part inaccurate-length-part %1 %2)]
    (testing "full part calculated length"
      (let [extracted (extract 500 1500)]
        (is (= 1131 (int (geo/line-string-length extracted))))
        (is (= [0 0] (first extracted)))
        (is (= [800 800] (last extracted)))))

    (testing "partial 500m geometry is longer than stated"
      (let [extracted (extract 750 1250)] ; 500m part
        (is (= (int (* (/ 1131.37 1000) 500))
               (int (geo/line-string-length extracted))))))))

(ns teet.drtest
  (:refer-clojure :exclude [atom])
  (:require [drtest.core :as drt]
            [drtest.step :as drt-step]
            [reagent.core :as r]))

(def step drt-step/step)

(def atom r/atom)

(ns teet.app-state
  (:require [reagent.core :as r]
            [reagent.ratom :as ratom]
            [teet.road.road-model :as road-model]))

(defonce app (r/atom {:config     {}
                      :navigation {:open? true}
                      :map        {:road-buffer-meters road-model/default-road-buffer-meters
                                   :layers [{:type :projects}]}}))

(defonce user (r/cursor app [:user]))

(defonce action-permissions (r/cursor app [:authorization/permissions]))

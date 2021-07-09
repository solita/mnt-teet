(ns teet.app-state
  (:require [reagent.core :as r]
            [teet.road.road-model :as road-model]))

(defonce  app (r/atom {:config     {}
                      :navigation {:open? false}
                      :map        {:road-buffer-meters (str road-model/default-road-buffer-meters)
                                   :layers [{:type :projects
                                             :id (random-uuid)}]}
                      :forms {:new-partner {:company/country :ee}}}))

(defonce user (r/cursor app [:user]))

(defonce action-permissions (r/cursor app [:authorization/permissions]))

(defonce contract-permissions (r/cursor app [:authorization/contract-permissions]))

(defonce config (r/cursor app [:config]))

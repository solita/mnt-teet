(ns teet.app-state
  (:require [reagent.core :as r]))


(defn- config [key]
  (.getAttribute js/document.body (str "data-" key)))

(defonce app (r/atom {:config {:api-url (config "api-url")
                               :login-url (config "login-url")}
                      :navigation {:open? true}}))

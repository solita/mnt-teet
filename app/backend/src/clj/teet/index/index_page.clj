(ns teet.index.index-page
  "Index page for TEET"
  (:require [hiccup.core :as hiccup]))

(defn index-page []
  [:html
   [:head
    ;; Fonts to support Material Design
    [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700&display=swap"}]
    ;; Icons to support Material Design
    [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/icon?family=Material+Icons"}]

    ;; Style elements for stylefy
    [:style {:id "_stylefy-constant-styles_"}]
    [:style {:id "_stylefy-styles_"}]

    [:script {:type "text/javascript"}
     "new Promise((resolve, reject) => {
        window.resolveOnload = resolve;
     })
     .then(() => { teet.main.main(); });"]

    [:body {:data-api-url (or (System/getenv "API_URL") "/")}
     [:div#teet-frontend]
     [:script {:src "main.js"}]
     [:script {:src "material-ui.production.min.js"}]]]])

(defn index-route []
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<!DOCTYPE html>\n"
              (hiccup/html (index-page)))})

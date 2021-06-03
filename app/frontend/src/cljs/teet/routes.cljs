(ns teet.routes
  "Routing for TEET app

  See also route definitions resources/routes.edn and route_macros.clj "
  (:require [bide.core :as r]
            [tuck.core :as tuck]
            [teet.app-state :as app-state]
            [teet.log :as log]
            [alandipert.storage-atom :refer [local-storage]])
  (:require-macros [teet.route-macros :refer [define-router]]))

(defrecord GoToUrl [url])

(defn- navigate [event {:keys [before-unload-message navigation-prompt-open?] :as app} navigate-fn]
  (if (and before-unload-message (not navigation-prompt-open?))
    (assoc app
           :navigation-prompt-open? true
           :navigation-confirm event)
    (navigate-fn (dissoc app
                         :navigation-prompt-open?
                         :before-unload-message
                         :navigation-confirm))))

(extend-protocol tuck/Event
  GoToUrl
  (process-event [{url :url :as e} app]
    (navigate e app
              (fn [app]
                (.setTimeout js/window #(set! (.-location js/window) url) 0)
                app))))

;; See routes.edn
(define-router teet-router)

(defmulti on-navigate-event
  "Determine event(s) to be run when user navigates to a given route.
  Returns a single Tuck event or a vector of Tuck events to be applied
  to the app state. Takes a map containing the navigation data as parameter.
  Route parameters are under the :params key."
  :page)

(defmulti on-leave-event
  "Determine event(s) to be run when user navigates away from the given route."
  :page)

(defmethod on-navigate-event :default [_] nil)
(defmethod on-leave-event :default [_] nil)

(defn- send-startup-events [e! event]
  (if (vector? event)
    ;; Received multiple events, apply them all
    (doseq [event event
            :when event]
      (e! event))
    ;; Apply single event
    (e! event)))

(declare navigate!)

(defonce api-token (local-storage (atom nil) "api-token"))

(defn- on-navigate [go-to-url-event route-name params query]
  (log/debug "on-navigate: route name" route-name)
  (swap! app-state/app
         (fn [{:keys [before-unload-message navigation-prompt-open? url] :as app}]
           (if (and before-unload-message (not navigation-prompt-open?))
             (let [new-url js/window.location.href]
               ;; push previous URL to the history (the one we want to stay on)
               (.pushState js/window.history #js {} js/document.title
                           url)
               ;; Open confirmation dialog and only go to new page
               ;; if the user confirms navigation.
               (assoc app
                 :navigation-prompt-open? true
                 :navigation-confirm (go-to-url-event new-url)))

             (if (not= (:url app) js/window.location.href)
               (let [e! (tuck/control app-state/app)
                     navigation-data {:page route-name
                                      :params params
                                      :query query
                                      :url js/window.location.href
                                      ;; Use the name of the page as route key instead of all params
                                      :route-key (name route-name)}
                     navigation-data (assoc navigation-data :current-app app)

                     event-leave (on-leave-event {:current-app app
                                                  :page (:page app)
                                                  :params (:params app)
                                                  :query (:query app)
                                                  :new-page route-name
                                                  :new-params params
                                                  :new-query query})
                     event-leave (if (vector? event-leave) event-leave [event-leave])
                     event-to (on-navigate-event navigation-data)
                     event-to (if (vector? event-to) event-to [event-to])
                     app (merge app (dissoc navigation-data :current-app))]

                 ;; scroll to top of page
                 (when-let [page (.querySelector js/document ".page")]
                   (set! (.-scrollTop page) 0))

                 ;; Send startup events (if any) immediately after returning from this swap
                 (when (or event-leave event-to)
                   (.setTimeout
                    js/window
                    (fn []
                      (send-startup-events e! (vec (concat event-leave event-to))))
                    0))
                 app)
               app)))))

(defn start! []
  (r/start! teet-router
            {:default :default-page
             :on-navigate (partial on-navigate ->GoToUrl)}))

(defn navigate!
  "Navigate to given page with optional route and query parameters.
  The navigation is done by setting a timeout and can be called from
  tuck process-event."
  ([page] (navigate! page nil nil))
  ([page params] (navigate! page params nil))
  ([page params query]
   (log/debug "in navigate! fn")
   (if page
     (.setTimeout js/window
                  #(r/navigate! teet-router page params query) 0)
     (log/warn "Cannot find page " page " to navigate"))))

(defn url-for [{:keys [page params query]}]
  (str "#" (r/resolve teet-router page params query)))

(ns teet.ui.date-picker
  "Date picker component"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [teet.ui.icons :as icons]
            [goog.events.EventType :as EventType]
            [cljs-time.format :as tf]
            [teet.ui.material-ui :refer [TextField IconButton Popover]]
            [teet.ui.icons :as icons]))


(defn- pilko-viikoiksi [vuosi kk]
  (let [kk (inc kk) ;; cljs-time käyttää luonnollisia kk numeroita
        kk-alku (t/date-time vuosi kk 1)
        ;;_ (log "kk-alku on: " kk-alku)
        viikon-alku (loop [pvm (t/date-time vuosi kk 1)]
                      (if (= 1 (t/day-of-week pvm))
                        pvm
                        (recur (t/minus pvm (t/days 1)))))
        kk-loppu (loop [pvm (t/date-time vuosi kk 28)]
                   (if (not= (t/month pvm) kk)
                     ;; mentiin yli seuraavaan kuukauten, palauta edellinen päivä
                     (t/minus pvm (t/days 1))

                     ;; vielä samassa kuussa, kelataan huomiseen
                     (recur (t/plus pvm (t/days 1)))))
        viikon-loppu (loop [pvm kk-loppu]
                       ;; Kelataan päivää sunnuntaihin asti eteenpäin, että saadaan täysi viikko
                       (if (= 7 (t/day-of-week pvm))
                         pvm
                         (recur (t/plus pvm (t/days 1)))))]
    (loop [paivat []
           p viikon-alku]
      (if (t/after? p viikon-loppu)
        (vec (partition 7 7 [] paivat))
        (recur (conj paivat p)
               (t/plus p (t/days 1)))))))

(defn- same-month? [d1 d2]
  (and (= (t/month d1) (t/month d2))
       (= (t/year d1) (t/year d2))))

(defn- same-day? [d1 d2]
  (and (= (t/day d1) (t/day d2))
       (same-month? d1 d2)))

(def months ["jan" "feb" "mar" "apr" "may" "jun" "jul" "aug" "sep" "oct" "nov" "dec"])
(def days ["mon" "tue" "wed" "thu" "fri" "sat" "sun"])
(def format (tf/formatter "dd.MM.yyyy"))

(defn- parse [s]
  (tf/parse format s))

(defn- unparse [d]
  (tf/unparse format d))

(defn- parse-opt [s]
  (try
    (parse s)
    (catch js/Object _
      nil)))

(defn- unparse-opt [d]
  (if d
    (unparse d)
    ""))

(defn date-picker
  "Luo uuden päivämäärävalinnan.

   Seuraavat optiot ovat mahdollisia:

   :value        current selected date (goog.date.Date)
   :year         year to show (defaults to current)
   :month        month to show (defaults to current) (0 - 11)
   :on-change    callback to set new selected date
   :selectable?  fn to call for each date to check if it should be selectable"
  [optiot]
  (let [pakota-suunta (:pakota-suunta optiot)
        sijainti-atom (atom :alas-vasen)
        now (or (:value optiot) (t/now))
        nayta (atom [(.getYear now) (.getMonth now)])]
    (r/create-class
     {:component-will-receive-props
      (fn [this & [_ optiot]]
        (when-let [pvm (:pvm optiot)]
          ;; päivitetään näytä vuosi ja kk
          (reset! nayta [(.getYear pvm) (.getMonth pvm)])))
      
      :reagent-render
      (fn [{:keys [value on-change selectable?] :as opts}]
        (let [[vuosi kk] @nayta
              naytettava-kk (t/date-time vuosi (inc kk) 1)
              naytettava-kk-paiva? #(same-month? naytettava-kk %)]
          [:table.pvm-valinta {:style (merge
                                       {:display (if @sijainti-atom "table" "none")
                                        ;; Etenkin jos kalenteri avataan ylöspäin, on tärkeää, että korkeus pysyy vakiona
                                        ;; Muuten otsikkorivi hyppii sen mukaan paljonko kuussa on päiviä.
                                        :height "200px"}
                                       (case @sijainti-atom
                                         :ylos-oikea {:bottom "100%" :left 0}
                                         :ylos-vasen {:bottom "100%" :right 0}
                                         :alas-oikea {:top "100%" :left 0}
                                         :alas-vasen {:top "100%" :right 0}
                                         {}))}
           [:thead.pvm-kontrollit
            [:tr
             [:td.pvm-edellinen-kuukausi.klikattava
              {:on-click #(do (.preventDefault %)
                              (swap! nayta
                                     (fn [[vuosi kk]]
                                       (if (= kk 0)
                                         [(dec vuosi) 11]
                                         [vuosi (dec kk)])))
                              nil)}
              (icons/navigation-chevron-left)]
             [:td {:col-span 5} [:span.pvm-kuukausi (nth months kk)] " " [:span.pvm-vuosi vuosi]]
             [:td.pvm-seuraava-kuukausi.klikattava
              {:on-click #(do (.preventDefault %)
                              (swap! nayta
                                     (fn [[vuosi kk]]
                                       (if (= kk 11)
                                         [(inc vuosi) 0]
                                         [vuosi (inc kk)])))
                              nil)}
              (icons/navigation-chevron-right)]]
            [:tr.pvm-viikonpaivat
             (for [paiva days]
               ^{:key paiva}
               [:td paiva])]]

           [:tbody.pvm-paivat
            (for [paivat (pilko-viikoiksi vuosi kk)]
              ^{:key (str (first paivat))}
              [:tr
               (for [paiva paivat
                     :let [valittava? (or (not (some? selectable?))
                                          (selectable? paiva))]]
                 ^{:key (str paiva)}
                 [:td.pvm-paiva {:class (str
                                         (if valittava?
                                           "klikattava "
                                           "pvm-disabloitu ")
                                         (let [now (t/now)]
                                           (when (and value (same-day? now value)) "pvm-tanaan "))
                                         (when (and value
                                                    (= (t/day paiva) (t/day value))
                                                    (= (t/month paiva) (t/month value))
                                                    (= (t/year paiva) (t/year value)))
                                           "pvm-valittu ")
                                         (if (naytettava-kk-paiva? paiva)
                                           "pvm-naytettava-kk-paiva" "pvm-muu-kk-paiva"))

                                 :on-click #(do (.stopPropagation %)
                                                (when valittava?
                                                  (on-change paiva))
                                                nil)}
                  (t/day paiva)])])]
           [:tfoot.pvm-tanaan-text
            [:tr [:td {:colSpan 7}
                  [:a {:on-click #(do
                                    (.preventDefault %)
                                    (.stopPropagation %)
                                    (on-change (t/now)))}
                   "Tänään"]]]]]))})))

(defn date-input
  "Combined text field and date picker input field"
  [{:keys [value on-change]}]
  (r/with-let [txt (r/atom (unparse-opt value))
               open? (r/atom false)
               ref (atom nil)]
    [:<>
     [TextField {:value @txt
                 :ref #(reset! ref %)
                 :on-change #(let [v (-> % .-target .-value)]
                               (reset! txt v)
                               (when-let [d (parse-opt v)]
                                 (on-change d)))}]
     [IconButton {:on-click #(reset! open? true)}
      [icons/action-calendar-today]]
     [Popover {:open @open?
               :anchorEl @ref
               :anchorOrigin {:vertical "bottom"
                              :horizontal "left"}}
      [date-picker {:value value
                    :on-change #(do
                                  (reset! txt (unparse-opt %))
                                  (on-change %)
                                  (reset! open? false))}]]]))

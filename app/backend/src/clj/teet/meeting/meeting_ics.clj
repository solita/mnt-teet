(ns teet.meeting.meeting-ics
  "Meeting iCalendar format"
  (:require [teet.meeting.meeting-model :as meeting-model]
            [clojure.string :as str]
            [teet.user.user-model :as user-model]))


(def ^:const vcal-wrapper
  ["BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//mnt/teet//NONSGML v1.0//EN\r\nMETHOD:REQUEST\r\nVERSION:2.0\r\n"
   "END:VCALENDAR"])

(def ^:const vevent-wrapper
  ["BEGIN:VEVENT\r\n"
   "END:VEVENT\r\n"])

(def ^:const vcal-cancel-wrapper
  ["BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//mnt/teet//NONSGML v1.0//EN\r\nMETHOD:CANCEL\r\nVERSION:2.0\r\n"
   "END:VCALENDAR"])

(def utc-tz (java.time.ZoneId/of "UTC"))
(def ical-date-format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd'T'HHmmss'Z'"))

(defn- ical-date [^java.util.Date dt]
  (-> dt
      .toInstant
      (.atZone utc-tz)
      (.format ical-date-format)))

(def ^:const fold-length 74)

(def escaped-chars [["\\" "\\\\"]
                    ["\n" "\\n"]
                    ["," "\\,"]
                    [";" "\\;"]])

(defn- escape-chars [text]
  (if text
    (reduce (fn [text [from to]]
              (str/replace text from to))
            text
            escaped-chars)
    ""))

(defn- fold-text [text]
  (if (<= (.length text) fold-length)
    text
    (let [first-line (subs text 0 fold-length)
          rest-of-text (subs text fold-length)]
      (str first-line "\r\n " (fold-text rest-of-text)))))

(defn vcal-properties [& names-and-values]
  (str/join ""
            (map (fn [[property value]]
                   (str (fold-text (str property ":" value)) "\r\n"))
                 (partition 2 names-and-values))))

(defn- meeting-description [{:meeting/keys [agenda]}]
  (str/join "\n\n"
            (map (fn [{:meeting.agenda/keys [topic responsible]}]
                   (str "- " topic
                        " (" (user-model/user-name responsible) ")"))
                 agenda)))

(defn meeting-ics
  "Create iCalendar event from meeting"
  [{id :db/id :meeting/keys [location start end organizer] :as meeting}]
  (let [[vcal-before vcal-after] vcal-wrapper
        [vevent-before vevent-after] vevent-wrapper]
    (str vcal-before
         vevent-before
         (vcal-properties
          "UID" (str id "@teet")
          "DTSTAMP" (ical-date (java.util.Date.))
          "ORGANIZER" (str "mailto:" (escape-chars (:user/email organizer)))
          "SUMMARY" (escape-chars (meeting-model/meeting-title meeting))
          "DESCRIPTION" (escape-chars (meeting-description meeting))
          "LOCATION" (escape-chars location)
          "DTSTART" (ical-date start)
          "DTEND" (ical-date end))

         vevent-after
         vcal-after)))

(defn cancel-meeting-ics
  [{id :db/id :meeting/keys [location start end organizer] :as meeting}]
  (let [[vcal-before vcal-after] vcal-cancel-wrapper
        [vevent-before vevent-after] vevent-wrapper]
    (str vcal-before
         vevent-before
         (vcal-properties
           "UID" (str id "@teet")
           "DTSTAMP" (ical-date (java.util.Date.))
           "ORGANIZER" (str "mailto:" (escape-chars (:user/email organizer)))
           "SUMMARY" (escape-chars (meeting-model/meeting-title meeting))
           "DESCRIPTION" (escape-chars (meeting-description meeting))
           "LOCATION" (escape-chars location)
           "DTSTART" (ical-date start)
           "DTEND" (ical-date end)
           "STATUS" "CANCELLED")
         vevent-after
         vcal-after)))

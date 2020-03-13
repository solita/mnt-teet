(ns teet.project.activity-model)

(def activity-in-progress-statuses
  #{:activity.status/valid :activity.status/other :activity.status/research :activity.status/in-progress})

(def activity-ready-statuses
  #{:activity.status/completed :activity.status/expired :activity.status/canceled})

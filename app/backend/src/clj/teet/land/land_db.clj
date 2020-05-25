(ns teet.land.land-db
  (:require [datomic.client.api :as d]
            [taoensso.timbre :as log]))

(defn project-estate-procedures [db project-eid & [{:keys [where in]}]]
  (let [extra-args (seq in)
        in (map first extra-args)
        args (map second extra-args)]
    (log/debug "query args "(into [db project-eid] args))
    (d/q {:query {:find '[(pull ?e [*
                                    {:estate-procedure/process-fees [*]}
                                    {:estate-procedure/third-party-compensations [*]}
                                    {:estate-procedure/priced-areas [*]}
                                    {:estate-procedure/compensations [*]}])]
                  :where (into '[[?e :estate-procedure/project ?p]] where)
                  :in (into ['$ '?p] in)}
          :args (into [db project-eid] args)})))

(defn project-estate-procedure-by-id [db project-eid procedure-db-id]
  (ffirst
   (project-estate-procedures db
                              project-eid
                              {:in {'?e procedure-db-id}})))

(ns teet.contract.contract-spec
  (:require [clojure.spec.alpha :as s]))

(s/def :thk.contract/edit-contract-details (s/keys :req [:thk.contract/number
                                                         :thk.contract/external-link
                                                         :ta/region
                                                         :thk.contract/signed-at]))

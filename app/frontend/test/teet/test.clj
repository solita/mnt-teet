(ns teet.test
  (:require [clj-chrome-devtools.cljs.test :as cljs-test]))


(defn -main [& _args]
  (cljs-test/run-tests {:js "out/main.js"
                        :runner "TEET_TESTS();"}
                       {:headless? true ; change to false for local test debugging
                        :root-paths #{"." "resources/public"}
                        :on-test-result (fn [{result :result}]
                                          (System/exit (if (= :ok result) 0 1)))}))

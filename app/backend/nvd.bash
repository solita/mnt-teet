#!/bin/bash

# remove ion dep because it isn't available in public repos
cp deps.edn deps.edn.backup
cat deps.edn.backup | grep -v "com.datomic/ion {:mvn/version \"0.9.35\"}" > deps.edn

clojure -A:clj-nvd check

# restore deps.edn
cp deps.edn.backup deps.edn

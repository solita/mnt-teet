#!/bin/sh

cd ../../app/backend/

clojure -A:run-local &

cd ../../ci/browser-tests

npm install

npx cypress run --config-file cypress-localdev.json

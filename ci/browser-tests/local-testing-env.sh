#!/bin/sh

cd ../../app/backend/

clojure -A:run-browser-test &

cd ../../ci/browser-tests

npm install

while ! nc -z localhost 4000; do
  sleep 1
done

CYPRESS_SITE_PASSWORD=testing123 npx cypress run --config-file cypress-localdev.json

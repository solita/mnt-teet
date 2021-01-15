#!/bin/sh

export AWS_ACCESS_KEY_ID=$1
export AWS_SECRET_ACCESS_KEY=$2
export AWS_REGION=eu-central-1

cd ../../app/frontend

./ci-build.sh

cd ../backend/

clojure -A:run-browser-test &

cd ../../ci/browser-tests

npm install

while ! nc -z localhost 4000; do
  sleep 1
done

CYPRESS_SITE_PASSWORD=testing123 npx cypress run --config-file cypress-localdev.json

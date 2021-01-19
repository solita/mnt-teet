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

CYPRESS_SITE_PASSWORD=testing123 npx cypress run --config-file cypress-ci.json

CYPRESS_EXIT_CODE=$?

aws s3 sync cypress/videos s3://teet-browser-test-documents/videos

exit $CYPRESS_EXIT_CODE

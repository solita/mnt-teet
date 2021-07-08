#!/bin/sh

export AWS_ACCESS_KEY_ID=$1
export AWS_SECRET_ACCESS_KEY=$2
export AWS_REGION=eu-central-1

cd ../../app/backend/

clojure -A:run-browser-test &

cd ../../ci/browser-tests

npm install

timeout 30s bash -c 'while ! nc -z localhost 4000; do sleep 1; done; echo Backend started ok'
if [ $? -eq 124 ]; then
    echo "Backend failed to start in 30 seconds"
    exit 1;
fi

npm install playwright
npx playwright install-deps
pip3 install playwright

python3 playwright/index_test.py

EXIT_CODE=$?

exit $EXIT_CODE

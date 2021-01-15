#!/bin/sh

echo runnit frontend test script
npm install

clojure -A:prod

npx cypress run --config-file /ci/browser-tests/cypress-localdev.json

echo test failed, trying to cat /tmp/*.edn in case they contain logs:
cat /tmp/*.edn
exit 1

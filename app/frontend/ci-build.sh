#!/bin/sh

npm install

clojure -A:dev

npx cypress open --config-file cypress-localdev.json

echo test failed, trying to cat /tmp/*.edn in case they contain logs:
cat /tmp/*.edn
exit 1

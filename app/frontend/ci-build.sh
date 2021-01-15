#!/bin/sh

npm install

clojure -A:prod

echo test failed, trying to cat /tmp/*.edn in case they contain logs:
cat /tmp/*.edn
exit 1

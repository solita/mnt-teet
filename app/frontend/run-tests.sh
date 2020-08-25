#!/bin/sh

npm install

clojure -A:testbuild
if clojure -A:testrun; then
  echo test successful
else
  echo test failed, trying to cat /tmp/*.edn in case they contain logs:
  cat /tmp/*.edn
  exit 1
fi


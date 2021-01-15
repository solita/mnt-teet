#!/bin/sh

echo runnit frontend test script
npm install

clojure -A:prod

echo frontend prod build ran

#!/bin/sh

echo running frontend test script
npm install

clojure -A:prod

echo frontend prod build ran

#!/bin/sh

echo running frontend test script
npm install

clojure -A:dev-build

echo frontend prod build ran

#!/bin/sh

echo running frontend test script
npm install

clojure -A:browser-test-build

echo frontend prod build ran

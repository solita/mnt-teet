#!/bin/sh

npm install

clojure -A:testbuild
clojure -A:testrun

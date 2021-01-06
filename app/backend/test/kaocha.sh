#!/usr/bin/env bash

clojure -A:test -m kaocha.runner --config-file test/tests.edn "$@"
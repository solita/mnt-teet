#!/usr/bin/env bash
set -eu

clojure -m figwheel.main -O advanced -bo prod

#!/usr/bin/env bash
set -eu
clojure -A:dev -m datomic.ion.dev "{:op :push :uname \"$1\"}"

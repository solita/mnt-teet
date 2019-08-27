#!/bin/sh
clojure -A:dev -m datomic.ion.dev "{:op :push :uname \"$1\"}"

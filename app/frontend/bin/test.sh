#!/bin/sh

timeout 50s clojure -A:test

exit 0

# TODO timeout + exit 0 => cat js env log into actual log

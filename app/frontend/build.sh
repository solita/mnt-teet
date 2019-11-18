#!/usr/bin/env bash
set -eu

clojure -m figwheel.main -O advanced -bo prod

branch=`git branch | grep "*" | cut -f2 -d' '`
githash=`git rev-parse HEAD`
buildtime=`date "+%d.%m.%Y %H:%M:%S"`

echo "window.teet_branch = \"${branch}\"; window.teet_githash = \"${githash}\"; window.teet_buildtime = \"${buildtime};" > target/public/cljs-out/version-info.js

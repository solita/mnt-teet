#!/usr/bin/env bash
set -eu

npm install

clojure -A:prod

branch="$CODEBUILD_SOURCE_VERSION"
#`git branch | grep "*" | cut -f2 -d' '`

githash=`git rev-parse HEAD`
buildtime=`date "+%d.%m.%Y %H:%M:%S"`

MAIN=out/main.js

echo "" >> $MAIN
echo "window.teet_branch = \"${branch}\"; window.teet_githash = \"${githash}\"; window.teet_buildtime = \"${buildtime}\";" >> $MAIN

AUTHZ=`cat ../backend/resources/authorization.edn | tr -d '\n'`
echo "window.teet_authz = \"${AUTHZ}\";" >> $MAIN

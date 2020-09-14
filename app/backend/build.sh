#!/usr/bin/env bash

set -eu

#echo "{:git-commit \"$CODEBUILD_RESOLVED_SOURCE_VERSION\"}" > resources/build-info.edn
COMMITHASH="$1"
if [ -z "$COMMITHASH" ]; then
    echo commithash arg missing
    exit 1
else
    clojure -Aion -m datomic.ion.dev '{:op :push :region "eu-central-1" :rev "'$COMMITHASH'"}'
fi

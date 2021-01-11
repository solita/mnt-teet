#!/usr/bin/env bash

set -eu

echo "{:git-commit \"$CODEBUILD_RESOLVED_SOURCE_VERSION\"}" > resources/build-info.edn
clojure -Aion -m datomic.ion.dev \
        "{:op :push :region \"eu-central-1\" :uname \"$CODEBUILD_RESOLVED_SOURCE_VERSION\"}"

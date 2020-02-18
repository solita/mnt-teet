#!/usr/bin/env bash

set -eu

GIT_COMMIT=`git rev-parse HEAD`

echo "{:git-commit \"$GIT_COMMIT\"}" > resources/build-info.edn

#clojure -A:pack -m mach.pack.alpha.jib \
#        --image-name $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$IMAGE_REPO_NAME:$IMAGE_TAG \
#        --image-type registry \
#        -m teet.main

clojure -Adev -m datomic.ion.dev '{:op :push :region "eu-central-1"}'

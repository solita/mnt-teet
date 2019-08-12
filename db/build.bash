#!/bin/bash

set -eu

clojure -A:pack -m mach.pack.alpha.jib \
        --image-name $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$IMAGE_REPO_NAME:$IMAGE_TAG \
        --image-type registry \
        -m migrate

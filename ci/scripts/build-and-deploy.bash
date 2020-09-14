#!/bin/bash

set -e

DIR=`dirname "$0"`
TEET_ENV=`aws ssm get-parameters --names /teet/env --query Parameters[0].Value --output text`


echo "============================"
echo "BUILD AND DEPLOY TEET"
echo "ENV: $TEET_ENV"
echo "Enter branch name to deploy:"
read VERSION

if echo "$VERSION" | grep -E -q '^[0-9a-f]{40,}$'; then
    LATEST_COMMIT="$VERSION"
else  
    LATEST_COMMIT=`git ls-remote https://github.com/solita/mnt-teet | grep refs/heads/$VERSION | cut -f1`
fi

if [ -z "$LATEST_COMMIT" ]; then
   echo "Unable to determine latest commit for branch $VERSION, exiting."
   exit 1;
fi

echo "Building and deploying version $VERSION that has latest commit in GitHub $LATEST_COMMIT"

AWS_REGION=us-east-1 AWS_DEFAULT_REGION=us-east-1 time $DIR/codebuild teet-datomic-ion $VERSION
time $DIR/codebuild teet-frontend $VERSION
time $DIR/codebuild teet-deploy $VERSION

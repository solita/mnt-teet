#!/bin/bash

set -e

DIR=`dirname "$0"`
TEET_ENV=`aws ssm get-parameters --names /teet/env --query Parameters[0].Value --output text`


echo "============================"
echo "BUILD AND DEPLOY TEET"
echo "ENV: $TEET_ENV"
echo "Enter branch name to deploy:"
read VERSION

LATEST_COMMIT=`git ls-remote https://github.com/solita/mnt-teet | grep refs/heads/$VERSION | cut -f1`

if [ -z "$LATEST_COMMIT" ]; then
   echo "Unable to determine latest commit for branch $VERSION, exiting."
   exit 1;
fi

echo "Building and deploying version $VERSION that has latest commit in GitHub $LATEST_COMMIT"

AWS_DEFAULT_REGION=us-east-1 $DIR/codebuild teet-datomic-ion $VERSION
$DIR/codebuild teet-frontend $VERSION
$DIR/codebuild teet-deploy $VERSION

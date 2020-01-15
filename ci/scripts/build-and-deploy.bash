#!/bin/bash

set -e

DIR=`dirname "$0"`
TEET_ENV=`aws ssm get-parameters --names /teet/env --query Parameters[0].Value --output text`


echo "============================"
echo "BUILD AND DEPLOY TEET"
echo "ENV: $TEET_ENV"
echo "Enter branch name to deploy:"
read VERSION

echo "Building $VERSION"
exit;

AWS_DEFAULT_REGION=us-east-1 $DIR/codebuild teet-datomic-ion $VERSION
$DIR/codebuild teet-frontend $VERSION
$DIR/codebuild teet-deploy $VERSION

#!/usr/bin/env bash
set -euo pipefail

STACK_NAMES="teet-api teet-infra teet-ci teet-env teet-thk"
aws help > /dev/null # check it exists and runs
which jq > /dev/null # need jq, should be in codebuild images by default
aws sts get-caller-identity # check our creds are good
for stack in $STACK_NAMES; do
    aws cloudformation detect-stack-drift --stack-name $stack --output text > stack-id.txt
    aws cloudformation describe-stack-drift-detection-status --stack-drift-detection-id=$(cat stack-id.txt)  --output json > drift-status.json
    status=$(jq -r .StackDriftStatus < driftstatus.json)
    if [ $status = DRIFTED ]; .. 
    
done


#!/usr/bin/env bash
set -euo pipefail

STACK_NAMES="teet-api teet-infra teet-ci teet-env teet-thk"
aws help > /dev/null # check it exists and runs
which jq > /dev/null # need jq, should be in codebuild images by default
aws sts get-caller-identity # check our creds are good
drifts_seen=false
for stack in $STACK_NAMES; do
    echo drift detection starting on stack: $stack
    aws cloudformation detect-stack-drift --stack-name $stack --output text > stack-id.txt
    while true; do
      aws cloudformation describe-stack-drift-detection-status --stack-drift-detection-id=$(cat stack-id.txt)  --output json > drift-status.json
      drift_status=$(jq -r .StackDriftStatus < drift-status.json)
      detection_status=$(jq -r .DetectionStatus < drift-status.json)
      if [ $drift_status = DRIFTED ]; then
	  echo drift detected in stack: $stack:
	  cat drift-status.json	  
	  drifts_seen=true
	  break
      elif [ $drift_status = IN_SYNC ]; then
	  echo stack in sync: $stack
	  break
      elif [ $detection_status = DETECTION_FAILED ]; then
	  echo drift detection failed, status json:
	  cat drift-status.json
	  exit 2
      elif [ $detection_status = DETECTION_IN_PROGRESS ]; then
	  echo 'waiting for detection to complete...'
	  sleep 3.14
	  continue
      else
	  echo unhandled drift status: "$drift_status"
	  echo drift status json:
	  cat drift-status.json
	  exit 3
      fi     
    done
done
if [ $drifts_seen = true ]; then
    exit 1
else
    exit 0
fi


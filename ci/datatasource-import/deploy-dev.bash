#!/usr/bin/env bash
aws --region eu-central-1 cloudformation deploy  --stack-name teet-datasource-import --template $PWD/import.yml
aws --region eu-central-1 cloudformation describe-stack-events --stack-name teet-datasource-import

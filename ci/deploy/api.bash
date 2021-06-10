#!/bin/bash

TEET_CLUSTER_NAME=`aws ssm get-parameters --names "/teet/ecs/cluster_name"  --query 'Parameters[*].Value' --output text`
TEET_SERVICE_NAME=`aws ssm get-parameters --names "/teet/ecs/service_name"  --query 'Parameters[*].Value' --output text`

aws ecs update-service --cluster $TEET_CLUSTER_NAME --service $TEET_SERVICE_NAME --force-new-deployment

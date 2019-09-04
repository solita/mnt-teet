#!/bin/bash

aws ecs update-service --cluster teet-dev --service teet-api-test --force-new-deployment

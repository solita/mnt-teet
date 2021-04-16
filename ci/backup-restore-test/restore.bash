#!/usr/bin/env bash

echo "Check started"

export BACKUP_FILE_NAME=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $4}')

echo $BACKUP_FILE_NAME

echo "Check completed"
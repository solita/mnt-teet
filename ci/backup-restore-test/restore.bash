#!/usr/bin/env bash

echo "Restore started"

export min_backup_size=1000000
export BACKUP_SIZE=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $3}')
echo $BACKUP_SIZE

if (( BACKUP_SIZE < min_backup_size)); then
  echo "Backup is too small"
  exit 1;
fi

export BACKUP_FILE_NAME=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $4}')

echo $BACKUP_FILE_NAME

aws lambda invoke --function-name teet-datomic-Compute-restore --payload "{\"input\": '{\"bucket\":\"teet-dev-documents\",\"file-key\":\"$BACKUP_FILE_NAME\"}'}" out

echo "Restore completed"




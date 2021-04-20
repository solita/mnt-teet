#!/usr/bin/env bash

echo "Restore started"

export min_backup_size=1000000
export BACKUP_SIZE=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $3}')
export RESTORE_DB_NAME="teet"$(date +%Y%m%d)"debug1"
export RESTORE_ASSET_DB_NAME="teetasset"$(date +%Y%m%d)"debug1"
echo $BACKUP_SIZE
echo $RESTORE_DB_NAME
echo $RESTORE_ASSET_DB_NAME

if [ "$BACKUP_SIZE" -lt "$min_backup_size" ]; then
  echo "Backup is too small"
  exit 1;
fi

export BACKUP_FILE_NAME=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $4}')

echo $BACKUP_FILE_NAME

aws lambda invoke --function-name teet-datomic-Compute-restore --payload "{
  \"bucket\":\"teet-dev2-documents\",
  \"file-key\":\"$BACKUP_FILE_NAME\",
  \"create-database\":\"$RESTORE_DB_NAME\",
  \"create-asset-database\":\"$RESTORE_ASSET_DB_NAME\"}" out

echo $(cat out)

echo "Restore completed"




#!/usr/bin/env bash

echo "Restore started"

export min_backup_size=1000000
export BACKUP_SIZE=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $3}')
export RESTORE_DB_NAME="teet"$(date +%Y%m%d)"debug2"
export RESTORE_ASSET_DB_NAME="teetasset"$(date +%Y%m%d)"debug2"

echo $RESTORE_DB_NAME
echo $RESTORE_ASSET_DB_NAME

if [ "$BACKUP_SIZE" -lt "$min_backup_size" ]; then
  echo "Backup is too small"
  exit 1;
fi
export S3_BUCKET="teet-dev2-documents"
export BACKUP_FILE_NAME=$(aws s3 ls s3://teet-dev2-documents | grep "backup" | grep $(date +%Y-%m-%d) | awk '{print $4}')

echo $BACKUP_FILE_NAME

#aws lambda invoke --function-name teet-datomic-Compute-restore --payload "{
#  \"bucket\":\"teet-dev2-documents\",
#  \"file-key\":\"$BACKUP_FILE_NAME\",
#  \"create-database\":\"$RESTORE_DB_NAME\",
#  \"create-asset-database\":\"$RESTORE_ASSET_DB_NAME\"}" out

export SECONDS=$(date +%s)
export END_TIME=$((${SECONDS}+300))
interval=10

echo $SECONDS
echo $END_TIME
# polling 5 min for .log file
while [ "$SECONDS" -lt "$END_TIME" ]; do
  aws s3api head-object --bucket $S3_BUCKET --key "$BACKUP_FILE_NAME" || not_exist=true
  echo $API_RESPONSE
  if [ $not_exist ]; then
    echo "Polling for successfully completed restore log"
  else
    echo "Restore successfully completed."
    exit 0
  fi
  sleep ${interval}
done

echo "Restore was not completed in time."
exit 1




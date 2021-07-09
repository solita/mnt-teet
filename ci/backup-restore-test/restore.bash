#!/usr/bin/env bash
set -eu

echo "Restore started"
S3_BUCKET="teet-dev2-documents"

BACKUP_FILE_NAME=$(aws s3 ls s3://$S3_BUCKET | grep "backup" | grep $(date +%Y-%m-%d)".edn.zip$" | awk '{print $4}')
echo $BACKUP_FILE_NAME
[ -z "$BACKUP_FILE_NAME" ] && echo "Backup file not found for " $(date +%Y-%m-%d) && exit 1

MIN_BACKUP_SIZE=1000000
BACKUP_SIZE=$(aws s3 ls s3://$S3_BUCKET | grep "backup" | grep $(date +%Y-%m-%d)".edn.zip$" | awk '{print $3}')
RESTORE_DB_NAME="teet"$(date +%Y%m%d)
RESTORE_ASSET_DB_NAME="teetasset"$(date +%Y%m%d)

echo "Restored DB name "$RESTORE_DB_NAME
echo "Restored Assets DB name "$RESTORE_ASSET_DB_NAME

if [ "$BACKUP_SIZE" -lt "$MIN_BACKUP_SIZE" ]; then
  echo "Backup is too small"
  exit 1;
fi

echo "Backup file name " $BACKUP_FILE_NAME

aws lambda invoke --cli-binary-format raw-in-base64-out --function-name teet-datomic-Compute-restore --payload "{
  \"bucket\":\"$S3_BUCKET\",
  \"file-key\":\"$BACKUP_FILE_NAME\",
  \"create-database\":\"$RESTORE_DB_NAME\",
  \"create-asset-database\":\"$RESTORE_ASSET_DB_NAME\"}" out

BACKUP_LOG_NAME=$BACKUP_FILE_NAME".log"

echo "Restore log file name " $BACKUP_LOG_NAME

# wait 10 min until restore completed
sleep 600

# polling 20 times 5 seconds each for .log file
aws s3api wait object-exists --bucket $S3_BUCKET --key "$BACKUP_LOG_NAME"

first_line_bytes=$(aws s3api get-object --bucket $S3_BUCKET --key $BACKUP_LOG_NAME --range bytes=0-256 /dev/stdout | head -1)
status=${first_line_bytes: 0: 7}
echo $status
if [ $status = "SUCCESS" ]; then
  echo "Restore successfully completed."
  exit 0
else
  # Cut the FAILURE prefix and { suffix
  msg=$(echo $first_line_bytes | cut -d'{' -f 1)
  err="${msg: 7: 249}"
  bash ./notify.bash "Backup-Restore build from $BACKUP_FILE_NAME failed.\n $err" ":blob-fail:"
  exit 1
fi

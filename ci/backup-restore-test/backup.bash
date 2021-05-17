#!/usr/bin/env bash
set -eu
echo "Backup started"

S3_BUCKET="teet-dev2-documents"
BACKUP_FILE_NAME="teet-dev2-backup-"$(date +%Y-%m-%d)".edn.zip"

aws lambda invoke --function-name teet-datomic-Compute-backup --payload '{"key": "value"}' out

echo "New backup file name " $BACKUP_FILE_NAME

SECONDS=$(date +%s)
END_TIME=$((${SECONDS}+600))
interval=10

echo $SECONDS
echo $END_TIME
# polling 10 min for .zip file
while [ "$SECONDS" -lt "$END_TIME" ]; do
  aws s3api wait object-exists --bucket $S3_BUCKET --key "$BACKUP_FILE_NAME" || not_exist=true
  if [ $not_exist ]; then
    echo "Polling for .zip"
  else
    echo "Backup successfully completed."
    exit 0
  fi
  sleep ${interval}
  SECONDS=$(date +%s)
done

echo "Backup timeout exceeded."
sh ./notifiy.bash "Backup-Restore build failed. Backup .zip pull timeout exceeded" ":blob-fail:"
exit 1

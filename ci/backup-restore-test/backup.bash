#!/usr/bin/env bash

echo "Backup started"

S3_BUCKET=$(aws ssm get-parameters --names "/teet/s3/backup-bucket" --query "Parameters[0].Value" | tr -d '"')
BACKUP_FILE_NAME="teet-dev2-backup-"$(date +%Y-%m-%d)".edn.zip"

aws lambda invoke --function-name teet-datomic-Compute-backup --payload '{"key": "value"}' out

echo "New backup file name " $BACKUP_FILE_NAME

SECONDS=$(date +%s)
END_TIME=$((${SECONDS}+600))
interval=10

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
bash ./notify.bash "Backup-Restore build failed. Backup .zip pull timeout exceeded" ":blob-fail:"
exit 1

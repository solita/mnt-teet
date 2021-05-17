#!/usr/bin/env bash

echo "Check started"

set -eu

RESTORE_DB_NAME="teet"$(date +%Y%m%d)
RESTORE_ASSET_DB_NAME="teetasset"$(date +%Y%m%d)

CURRENT_DB=$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"')
CURRENT_ASSET_DB=$(aws ssm get-parameters --names "/teet/datomic/asset-db-name" --query "Parameters[0].Value" | tr -d '"')

aws ssm put-parameter \
              --name "/teet/datomic/db-name" \
              --type "String" \
              --value $RESTORE_DB_NAME \
              --overwrite

aws ssm put-parameter \
              --name "/teet/datomic/asset-db-name" \
              --type "String" \
              --value $RESTORE_ASSET_DB_NAME \
              --overwrite

sleep 60

echo "Current DB "$CURRENT_DB
echo "Restored DB "$RESTORE_DB_NAME

echo "Current Asset DB "$CURRENT_ASSET_DB
echo "Restored Asset DB "$RESTORE_ASSET_DB_NAME

BASEURL=$(aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"' | sed 's|/$||g')
# "query/?q=["^ ","~:query","~:teet.system/db"]"
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

echo "curl returns $(curl -s -o /dev/null -w "%{http_code}" "$BASEURL/$ENDPOINT")"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASEURL/$ENDPOINT")

if [ "$STATUS" = "200" ]; then
  echo "Query succeeded"
  MSG_TEET_DB_IS_SWITCHED="$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"') DB is in use"
  MSG_ASSET_DB_IS_SWITCHED="$(aws ssm get-parameters --names "/teet/datomic/asset-db-name" --query "Parameters[0].Value" | tr -d '"') Assets DB is in use"

  echo $MSG_TEET_DB_IS_SWITCHED
  echo $MSG_ASSET_DB_IS_SWITCHED

    # Cleanup both old DB-s
  aws lambda invoke --function-name teet-datomic-Compute-delete-db --payload "{
    \"db-name\":\"$CURRENT_DB\",
    \"asset-db-name\":\"$CURRENT_ASSET_DB\"}" out

  echo "$CURRENT_DB and $CURRENT_ASSET_DB have been deleted"

  sh ./notifiy.bash "Backup-Restore build succeeded \n$MSG_TEET_DB_IS_SWITCHED\n$MSG_ASSET_DB_IS_SWITCHED" ":great-success:"

  echo "Backup-Restore build succeeded"
else
  echo "Restored DB check failed with status: $STATUS"
  aws ssm put-parameter \
              --name "/teet/datomic/db-name" \
              --type "String" \
              --value $CURRENT_DB \
              --overwrite

  aws ssm put-parameter \
              --name "/teet/datomic/asset-db-name" \
              --type "String" \
              --value $CURRENT_ASSET_DB \
              --overwrite

  MSG_TEET_DB_IS_RESTORED="$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"') is set back"
  MSG_ASSET_DB_IS_RESTORED="$(aws ssm get-parameters --names "/teet/datomic/asset-db-name" --query "Parameters[0].Value" | tr -d '"') is set back"
  echo $MSG_TEET_DB_IS_RESTORED
  echo $MSG_ASSET_DB_IS_RESTORED

  sh ./notifiy.bash "Backup-Restore build failed. Restored DB check failed with status: $STATUS\n$MSG_TEET_DB_IS_RESTORED\n$MSG_ASSET_DB_IS_RESTORED" ":blob-fail:"
  exit 1
fi
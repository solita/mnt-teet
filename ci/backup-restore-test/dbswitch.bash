#!/usr/bin/env bash

echo "Check started"

set -eu

RESTORE_DB_NAME="teet"$(date +%Y%m%d)
CURRENT_DB=$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"')

# aws ssm put-parameter \
#              --name "/teet/datomic/db-name" \
#              --type "String" \
#              --value "$RESTORE_DB_NAME" \
#              --overwrite

# sleep 60

echo "Current DB "$CURRENT_DB
echo "Restored DB "$RESTORE_DB_NAME

BASEURL=$(aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"' | sed 's|/$||g')
# "query/?q=["^ ","~:query","~:teet.system/db"]"
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"
echo "$BASEURL/$ENDPOINT"

echo curl returns $(curl -s -o /dev/null -w "%{http_code}" "$BASEURL/$ENDPOINT")
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASEURL/$ENDPOINT")

if [ "$STATUS" == "200" ]; then
    echo "Query succeeded"
    echo "$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"') is in use"
    break;
else
    echo "Query failed with status: $STATUS"
    # aws ssm put-parameter \
    #              --name "/teet/datomic/db-name" \
    #              --type "String" \
    #              --value "$CURRENT_DB" \
    #              --overwrite
    sleep 60
    echo "$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"') is set back"
fi

echo "Check completed"
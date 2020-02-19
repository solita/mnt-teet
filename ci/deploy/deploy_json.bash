#!/usr/bin/env bash
set -eu

PUBLICDIR=`aws ssm get-parameters --names "/teet/s3/publicdir" --query "Parameters[0].Value" | tr -d '"'`

if [ -z "$PUBLICDIR" ]
then
    echo "Empty public dir variable value"
    exit 1;
fi

if [ -z "$1" ]
then
    echo "No status provided"
    exit 1;
fi

# If setting status to deployed, make sure db is up and running
if [ $1 == "deployed" ]
then
    timeout 5m ./wait_for_datomic.bash || exit 1;
fi

echo "{\"commit\":\"$COMMIT\",\"status\":\"$1\",\"timestamp\":\"`date`\"}" > deploy.json
aws s3 cp deploy.json s3://$PUBLICDIR/js/deploy.json --acl public-read
rm deploy.json

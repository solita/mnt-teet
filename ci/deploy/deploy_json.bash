#!/usr/bin/env bash
set -eu

PUBLICDIR=`aws ssm get-parameters --names "/teet/s3/publicdir" --query "Parameters[0].Value" | tr -d '"'`
BASEURL=`aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"'`
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

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
    echo "Waiting for DB"
    until $(curl --output /dev/null --silent --head --fail "$BASEURL$ENDPOINT"); do
        printf '.'
        sleep 5
    done
fi

echo "{\"commit\":\"$COMMIT\",\"status\":\"$1\",\"timestamp\":\"`date`\"}" > deploy.json
aws s3 cp deploy.json s3://$PUBLICDIR/js/deploy.json --acl public-read
rm deploy.json

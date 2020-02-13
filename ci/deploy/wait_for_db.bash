#!/usr/bin/env bash
set -eu

BASEURL=`aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"'`
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

echo "Waiting for DB"''
while true
do
    STATUS=$(curl -s -o /dev/null -L -w '%{http_code}' "$BASEURL$ENDPOINT")
    if [ $STATUS -eq 200 ]; then
        break
    else
        echo -n .
    fi
    sleep 5
done

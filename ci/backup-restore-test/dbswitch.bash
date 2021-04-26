#!/usr/bin/env bash

echo "Check started"

set -eu

CURRENT_DB=$(aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"')

echo "Checking DB "$CURRENT_DB

BASEURL=$(aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"' | sed 's|/$||g')
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

echo "$BASEURL/$ENDPOINT"

while true
do
    echo curl returns $(curl -s -L -w ' %{http_code}\n' "$BASEURL/$ENDPOINT")
    read -r CURRENT_COMMIT STATUS < <(curl -s -L -w ' %{http_code}\n' "$BASEURL/$ENDPOINT")

    if [ "$STATUS" == "200" ] && [ "$CODEBUILD_RESOLVED_SOURCE_VERSION" == "$CURRENT_COMMIT" ]; then
        echo "Matching versions, query succeeded"
        break;
    else
        echo "Waiting, deployed: $CURRENT_COMMIT, expected: $CODEBUILD_RESOLVED_SOURCE_VERSION"
    fi
    sleep 5
done

echo "Check completed"
#!/usr/bin/env bash
set -eu

set -x

BASEURL=$(aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"' | sed 's|/$||g')
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

while true
do
    echo curl on "$BASEURL/$ENDPOINT" returns $(curl  "$BASEURL/$ENDPOINT")
    echo curl with s/L/w opts returns $(curl -s -L -w ' %{http_code}\n' "$BASEURL/$ENDPOINT")
    read -r CURRENT_COMMIT STATUS < <(curl -s -L -w ' %{http_code}\n' "$BASEURL/$ENDPOINT")    

    if [ "$STATUS" == "200" ] && [ "$CODEBUILD_RESOLVED_SOURCE_VERSION" == "$CURRENT_COMMIT" ]; then
        echo "Matching versions, deploy finished"
        break;
    else
        echo "Waiting, deployed: $CURRENT_COMMIT, expected: $CODEBUILD_RESOLVED_SOURCE_VERSION"
    fi
    sleep 5
done

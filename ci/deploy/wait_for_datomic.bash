#!/usr/bin/env bash
set -xeu

BASEURL=`aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"'`
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

while true
do
    read -r CURRENT_COMMIT STATUS < <(curl -s -L -w ' %{http_code}\n' "$BASEURL$ENDPOINT")

    echo "CURRENT_COMMIT: $CURRENT_COMMIT"
    echo "STATUS: $STATUS"
    if [ "$STATUS" == "200" ] && [ "$CODEBUILD_RESOLVED_SOURCE_VERSION" == "$CURRENT_COMMIT" ]; then
        echo "Matching versions, deploy finished"
        break;
    else
        echo "Versions don't match, should wait"
    fi
    sleep 5
done

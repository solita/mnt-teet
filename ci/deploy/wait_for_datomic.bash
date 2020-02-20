#!/usr/bin/env bash
set -eu

echo "In wait_for_datomic.bash";

BASEURL=`aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"'`
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

while true
do
    curl -s -L -w '\n%{http_code}' "$BASEURL$ENDPOINT" | {
        read CURRENT_COMMIT
        read STATUS
    }
    # TODO: Remove after testing
    echo "COMMIT: $CODEBUILD_RESOLVED_SOURCE_VERSION"
    echo "CURRENT_COMMIT: $CURRENT_COMMIT"
    echo "STATUS: $STATUS"
    if [ "$STATUS" == "200" ] && [ "$CODEBUILD_RESOLVED_SOURCE_VERSION" == "$CURRENT_COMMIT" ]; then
        echo "Matching versions, deploy finished";
        break;
    fi
    sleep 5
done

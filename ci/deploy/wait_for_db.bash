#!/usr/bin/env bash
set -eu

BASEURL=`aws ssm get-parameters --names "/teet/base-url" --query "Parameters[0].Value" | tr -d '"'`
ENDPOINT="query/?q=%5B%22%5E%20%22%2C%22~%3Aquery%22%2C%22~%3Ateet.system%2Fdb%22%5D"

echo "Waiting for DB"''
while true
do
    curl -s -L -w '\n%{http_code}' "$BASEURL$ENDPOINT" | {
        read CURRENT_COMMIT
        read STATUS
    }
    # TODO: Remove after testing
    echo $COMMIT
    echo $CURRENT_COMMIT
    echo $STATUS
    if [ $STATUS -eq 200 ] && [ "$COMMIT" == "$CURRENT_COMMIT" ]; then
        echo "AWWYISS";
        break;
    else
        echo -n .
    fi
    sleep 5
done

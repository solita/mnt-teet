#!/usr/bin/env bash
set -eu

echo "Waiting for DB"
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

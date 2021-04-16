#!/usr/bin/env bash

echo "Backup started"

aws lambda invoke --function-name teet-datomic-Compute-backup --payload '{"key": "value"}' out

echo $(cat out)

sed -i'' -e 's/"//g' out

echo $(cat out)

sleep 15
aws logs get-log-events --log-group-name /aws/lambda/teet-datomic-Compute-backup --log-stream-name $(cat out) --limit 5

echo "Backup completed"
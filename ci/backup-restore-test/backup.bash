#!/usr/bin/env bash

echo "Backup started"

aws lambda invoke --function-name teet-datomic-Compute-backup --payload '{"key": "value"}' out

sed -i'' -e 's/"//g' out
echo $(cat out)

sleep 15

echo "Backup completed"
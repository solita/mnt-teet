#!/usr/bin/env bash

echo "Backup started"

aws lambda invoke --function-name teet-datomic-Compute-backup --payload '{"key": "value"}' out

echo $(cat out)

echo "Backup completed"
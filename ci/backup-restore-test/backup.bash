#!/usr/bin/env bash

echo "Backup started"

aws lambda invoke --function-name teet-datomic-Compute-backup --payload '{"key": "value"}'

sleep 15

echo "Backup completed"
#!/usr/bin/env bash

set -e

export TEET_DB=`aws ssm get-parameters --names "/teet/datomic/db-name" --query "Parameters[0].Value" | tr -d '"'`

cat > catalog/teet-datomic.properties <<EOF
connector.name=datomic
datomic.databases[$TEET_DB]
EOF

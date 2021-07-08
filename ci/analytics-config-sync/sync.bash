#!/usr/bin/env bash

set -euo pipefail
export PATH=$PATH:/usr/local/bin

DATOMIC_SYSTEM_NAME=teet-datomic

function ssm-get {
    aws ssm get-parameter --name "$1" --query "Parameter.Value" --output text
}

MAIN_DB_NAME="$(ssm-get /teet/datomic/db-name)"
ASSET_DB_NAME="$(ssm-get /teet/datomic/asset-db-name)"
mkdir -p  a-cfg/catalog
cat > a-cfg/catalog/$DATOMIC_SYSTEM_NAME.properties <<EOF
connector.name=datomic
datomic.databases=[$ASSET_DB_NAME $MAIN_DB_NAME]
EOF
/usr/local/bin/datomic analytics sync $DATOMIC_SYSTEM_NAME a-cfg

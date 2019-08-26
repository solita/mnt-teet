#!/usr/bin/env bash

set -uoe

function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

ARGS="-h localhost -U postgres"
PSQL="psql $ARGS -c"
PSQL_TEET="psql -h localhost -U teet -d teet_template -c"

echo "Dropping and recreating teet_template database."
$PSQL "DROP DATABASE IF EXISTS teet_template;"
$PSQL "CREATE DATABASE teet_template;"
echo "Restoring teeregister dump"
$PSQL "CREATE EXTENSION IF NOT EXISTS postgis;"
$PSQL "DROP ROLE IF EXISTS teeregister;"
$PSQL "CREATE ROLE teeregister;"
aws s3 cp s3://teet-dev-files/db/teeregister.full.dump.bz2 - | bzcat | psql $ARGS teet_template


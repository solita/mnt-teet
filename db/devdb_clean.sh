#!/usr/bin/env bash


function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

ARGS="-h localhost -U postgres"
PSQL="psql $ARGS -c"
PSQL_TEET="psql -h localhost -U teet -c"

echo "Dropping and recreating teis database."
$PSQL "DROP DATABASE IF EXISTS teet;"
$PSQL "DROP ROLE IF EXISTS authenticator;"
$PSQL "DROP ROLE IF EXISTS teet_anon;"
$PSQL "DROP ROLE IF EXISTS teet_user;"
$PSQL "DROP ROLE IF EXISTS teet;"
$PSQL "CREATE ROLE teet WITH LOGIN SUPERUSER;"
$PSQL "CREATE DATABASE teet OWNER teet;"
$PSQL "CREATE ROLE authenticator LOGIN;"


echo "Restoring teeregister dump"
$PSQL "CREATE EXTENSION postgis;"
$PSQL "CREATE ROLE teeregister;"
aws s3 cp s3://teet-dev-files/db/teeregister.full.dump.bz2 - | bzcat | psql $ARGS teet

echo "Running migrations"
mvn flyway:migrate

echo "Adding all privileges in schema teet to teet_anon."
$PSQL_TEET "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA teet TO teet_anon;"

echo "Adding all privileges in schema teet to teet_anon."
$PSQL_TEET "GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA teet TO teet_user;"

echo "Importing THK projects with teet.thk.thk-import/run-test-import!"
aws s3 cp s3://teet-dev-files/db/THK_export.csv - | (cd ../app/backend; clj -m teet.thk.thk-import)

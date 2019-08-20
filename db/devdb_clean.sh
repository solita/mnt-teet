#!/usr/bin/env bash


function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

PSQL="psql -h localhost -U postgres -c"

echo "Dropping and recreating teis database."
$PSQL "DROP DATABASE IF EXISTS teet;"
$PSQL "DROP ROLE IF EXISTS authenticator;"
$PSQL "DROP ROLE IF EXISTS teet_anon;"
$PSQL "DROP ROLE IF EXISTS teet_user;"
$PSQL "DROP ROLE IF EXISTS teet;"
$PSQL "CREATE ROLE teet WITH LOGIN SUPERUSER;"
$PSQL "CREATE DATABASE teet OWNER teet;"
$PSQL "CREATE ROLE authenticator LOGIN;"

echo "Running migrations"
mvn flyway:migrate

echo "Adding all privileges in schema teet to teet_anon."
psql -h localhost -U teet -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA teet TO teet_anon;"

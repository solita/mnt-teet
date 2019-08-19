#!/usr/bin/env bash


function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

echo "Dropping and recreating teis database."
psql -h localhost -U postgres -c "DROP DATABASE IF EXISTS teet;"
psql -h localhost -U postgres -c "DROP ROLE IF EXISTS authenticator;"
psql -h localhost -U postgres -c "DROP ROLE IF EXISTS teet_anon;"
psql -h localhost -U postgres -c "DROP ROLE IF EXISTS teet_user;"
psql -h localhost -U postgres -c "DROP ROLE IF EXISTS teet;"
psql -h localhost -U postgres -c "CREATE ROLE teet WITH LOGIN SUPERUSER;"
psql -h localhost -U postgres -c "CREATE DATABASE teet OWNER teet;"

echo "Running migrations"
mvn flyway:migrate

echo "Adding all privileges in schema teet to teet_anon."
psql -h localhost -U teet -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA teet TO teet_anon;"

#!/usr/bin/env bash


function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

echo "Dropping and recreating teis database."
psql -c "DROP DATABASE teet;"
psql -c "DROP ROLE authenticator;"
psql -c "DROP ROLE teet_anon;"
psql -c "DROP ROLE teet_user;"
psql -c "CREATE DATABASE teet OWNER teet;"

echo "Running migrations"
mvn flyway:migrate

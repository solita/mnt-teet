#!/usr/bin/env bash


function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

echo "Dropping and recreating teis database."
psql -h localhost -U postgres -c "DROP DATABASE teet;"
psql -h localhost -U postgres -c "DROP ROLE authenticator;"
psql -h localhost -U postgres -c "DROP ROLE teet_anon;"
psql -h localhost -U postgres -c "DROP ROLE teet_user;"
psql -h localhost -U postgres -c "CREATE DATABASE teet OWNER teet;"

echo "Running migrations"
mvn flyway:migrate

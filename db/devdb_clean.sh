#!/bin/sh


function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

echo "Dropping and recreating teis database."
psql -c "DROP DATABASE teis;"
psql -c "CREATE DATABASE teis OWNER teis;"

echo "Running migrations"
mvn flyway:migrate

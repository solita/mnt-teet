#!/usr/bin/env bash
set -eu

function check_psql {
    if ! [ -x "$(command -v psql)" ]; then
        echo "psql not found, make sure it is in PATH" >&2
        exit 1
    fi
}

function check_curl {
    if ! [ -x "$(command -v curl)" ]; then
        echo "curl not found, make sure it is in PATH" >&2
        exit 1
    fi
}

function check_unzip {
    if ! [ -x "$(command -v unzip)" ]; then
        echo "unzip not found, make sure it is in PATH" >&2
        exit 1
    fi
}

function check_ogr2ogr {
    if ! [ -x "$(command -v ogr2ogr)" ]; then
        echo "ogr2ogr not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_psql

ARGS="-h localhost -U postgres"
PSQL="psql $ARGS -c"
PSQL_TEET="psql -h localhost -U teet -c"

echo "Dropping and recreating teet database."
$PSQL "DROP DATABASE IF EXISTS teet;"
$PSQL "DROP ROLE IF EXISTS authenticator;"
$PSQL "DROP ROLE IF EXISTS teet_anon;"
$PSQL "DROP ROLE IF EXISTS teet_user;"
$PSQL "DROP ROLE IF EXISTS teet;"
$PSQL "CREATE ROLE teet WITH LOGIN SUPERUSER;"
$PSQL "CREATE DATABASE teet TEMPLATE teet_template OWNER teet;" || {
    echo if the above failed with error about missing template, you need to run devdb_create_template.sh script first.
    exit 1
}
$PSQL "CREATE ROLE authenticator LOGIN;"

echo "Running migrations"
mvn flyway:baseline -Dflyway.baselineVersion=0
mvn flyway:migrate

echo "Adding all privileges in schema teet to teet_anon."
$PSQL_TEET "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA teet TO teet_anon;"

echo "Adding all privileges in schema teet to teet_anon."
$PSQL_TEET "GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA teet TO teet_user;"

echo "Importing THK projects with teet.thk.thk-import/run-test-import!"
aws s3 cp s3://teet-dev-files/db/THK_export.csv - | (cd ../app/backend && clj -m teet.thk.thk-import)

echo "Importing Maa-amet restrictions data dump"
check_curl
check_unzip
check_ogr2ogr
curl "https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_GPKG.zip" -o temp.zip
unzip temp.zip
ogr2ogr -f "PostgreSQL" PG:"host=localhost user=teet dbname=teet" KITSENDUSED.gpkg -lco schema=restrictions -lco spatial_index=yes
rm KITSENDUSED.gpkg
rm temp.zip

$PSQL_TEET "SELECT public.ensure_restrictions_indexes();"

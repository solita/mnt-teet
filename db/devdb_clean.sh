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

RESTRICTIONS_DUMP_FILE=KITSENDUSED.gpkg

echo "Importing Maa-amet restrictions data dump"
if [ ! -f "$RESTRICTIONS_DUMP_FILE" ]; then
    echo "- Downloading the dump"
    check_curl
    check_unzip
    curl "https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_GPKG.zip" -o temp.zip
    unzip temp.zip
fi

echo "- Running ogr2ogr"
check_ogr2ogr
ogr2ogr -f "PostgreSQL" PG:"host=localhost user=teet dbname=teet" KITSENDUSED.gpkg -lco schema=restrictions

if [ -f temp.zip ]; then
    echo "- Removing the temporary zip file"
    rm temp.zip
fi

echo "- Ensuring restriction indices exist"
$PSQL_TEET "SELECT public.ensure_restrictions_indexes();"


CADASTRE_DUMP_FILE=KATASTRIYKSUS.gpkg
echo "Import cadastral data dump"
if [ ! -f "$CADASTRE_DUMP_FILE" ]; then
    echo "- Downloading fixed dump from S3"
    aws s3 cp s3://teet-dev-files/db/KATASTRIYKSUS.gpkg .
    aws s3 cp s3://teet-dev-files/db/KATASTRIYKSUS.gpkg-wal .
    aws s3 cp s3://teet-dev-files/db/KATASTRIYKSUS.gpkg-shm .
fi

echo "- Running ogr2ogr"
ogr2ogr -f "PostgreSQL" PG:"host=localhost user=teet dbname=teet" $CADASTRE_DUMP_FILE -lco schema=cadastre -lco overwrite=yes

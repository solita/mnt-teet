#!/usr/bin/env bash
set -eu

function ensure-command-exists {
    if ! [ -x "$(command -v $1)" ]; then
        echo "$1 not found, make sure it is in PATH" >&2
        exit 1
    fi
}

ensure-command-exists psql
ensure-command-exists aws
ensure-command-exists curl
ensure-command-exists unzip
ensure-command-exists ogr2ogr


ARGS="-h localhost -U postgres"
PSQL="psql $ARGS -c"
PSQL_TEET="psql -h localhost -U teet -c"

echo "Dropping and recreating teet database."
$PSQL "DROP DATABASE IF EXISTS teet;"
$PSQL "DROP ROLE IF EXISTS authenticator;"
$PSQL "DROP ROLE IF EXISTS teet_anon;"
$PSQL "DROP ROLE IF EXISTS teet_user;"
$PSQL "DROP ROLE IF EXISTS teet_backend;"
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

RESTRICTIONS_DUMP_FILE=KITSENDUSED.gpkg

echo "Importing Maa-amet restrictions data dump"
if [ ! -f "$RESTRICTIONS_DUMP_FILE" ]; then
    echo "- Downloading the dump"
    curl "https://geoportaal.maaamet.ee/docs/KPO/KITSENDUSED_GPKG.zip" -o temp.zip
    unzip temp.zip
fi

function docker-ogr2ogr {
    local db_container_name=teetdb
    local teetdb_network_name=$(docker inspect $db_container_name | jq -r '.[].NetworkSettings.Networks | keys[]')
    if [ -f $RESTRICTIONS_DUMP_FILE ]; then
	MO1="--mount type=bind,source=$PWD/$RESTRICTIONS_DUMP_FILE,target=/$RESTRICTIONS_DUMP_FILE,readonly"
    else
	MO1=""
    fi
    if [ -f $CADASTRE_DUMP_FILE ]; then
	MO2="--mount type=bind,source=$PWD/$CADASTRE_DUMP_FILE,target=/$CADASTRE_DUMP_FILE,readonly"
    else
	MO2=""
    fi
    
    docker run -it --rm \
	   --network $teetdb_network_name --link $db_container_name:db \
	   $MO1 $MO2 \
	   osgeo/gdal:alpine-small-latest ogr2ogr "$@"
}

if ogr2ogr --version 2>/dev/null | egrep -q 'GDAL (2\.[4-9]|[3-9])'; then
    ogr2ogr=ogr2ogr
    dbhost=localhost
else
    echo opting for dockerized ogr2ogr since no new enough ogr2ogr is locally installed, this will only work if you are using dockerized postgresql & your db container is called teetdb
    ogr2ogr=docker-ogr2ogr
    dbhost="db"
fi


echo "- Running ogr2ogr"
$ogr2ogr -f "PostgreSQL" PG:"host=$dbhost user=teet dbname=teet" KITSENDUSED.gpkg -lco schema=restrictions -lco overwrite=yes

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
$ogr2ogr -f "PostgreSQL" PG:"host=$dbhost user=teet dbname=teet" $CADASTRE_DUMP_FILE -lco schema=cadastre -lco overwrite=yes

aws s3 cp s3://teet-dev-files/db/THK_TEET_import.csv .
THK_CSV_FILE=$PWD/THK_TEET_import.csv
echo "Importing THK projects"
(cd ../app/backend && clj -m teet.util.thk-import-cli $THK_CSV_FILE)

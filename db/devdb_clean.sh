#!/usr/bin/env bash
set -eu

function ensure-command-exists {
    if ! [ -x "$(command -v $1)" ]; then
        echo "$1 not found, make sure it is in PATH" >&2
        exit 1
    fi
}

ensure-command-exists psql
ensure-command-exists mvn

ARGS="-h localhost -U postgres -w"
PSQL="psql $ARGS -c"
: ${PSQL_TEET_DB_OWNER:=teet} # can be teetmaster when using rds db backup
PSQL_TEET="psql -h localhost -U $PSQL_TEET_DB_OWNER -c"
PSQL_TEET_SUPERUSER="psql -h localhost -U postgres -c"
# removal of this was somewho included in the checksummed migrations but not its creation. also
# didn't help to add its creation to repeatable migrations as they're run last (?).
function remake-migration-prob-sproc {
    $PSQL_TEET_SUPERUSER 'CREATE OR REPLACE FUNCTION teet.replace_entity_ids(idlist TEXT)
RETURNS BOOLEAN
AS $$
DECLARE
BEGIN
  RETURN false;
END;
$$ LANGUAGE PLPGSQL;
' teet
}


echo "Dropping and recreating teet database."
$PSQL "DROP DATABASE IF EXISTS teet;"
$PSQL "DROP ROLE IF EXISTS authenticator;"
$PSQL "DROP ROLE IF EXISTS teet_anon;"
$PSQL "DROP ROLE IF EXISTS teet_user;"
$PSQL "DROP ROLE IF EXISTS teet_backend;"
$PSQL "DROP ROLE IF EXISTS teet;"
$PSQL "CREATE ROLE $PSQL_TEET_DB_OWNER WITH LOGIN SUPERUSER;"
$PSQL "CREATE DATABASE teet TEMPLATE teet_template OWNER $PSQL_TEET_DB_OWNER;" || {
    echo if the above failed with error about missing template, you need to run devdb_create_template.sh script first.
    exit 1
}
$PSQL "CREATE ROLE authenticator LOGIN;"

echo "Running migrations"
mvn flyway:baseline -Dflyway.baselineVersion=0
mvn flyway:migrate || remake-migration-prob-sproc
mvn flyway:migrate

# "SECURITY DEFINER" (= running with rights of user who defined them) functions apparently needs 
for user in teet_anon $PSQL_TEET_DB_OWNER; do
  echo "Adding all privileges in schema teet to teet_anon."
  $PSQL_TEET_SUPERUSER "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA teet TO $user;" teet
  
  echo "Adding all privileges in schema teet to teet_anon."
  $PSQL_TEET_SUPERUSER "GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA teet TO $user;" teet
done

echo "Done! Next, start your PostgREST server and import datasources by running 'clojure -A:import example-config.edn' in ../app/datasource-import/."

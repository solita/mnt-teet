@echo off
SET "PSQL=psql -h localhost -U postgres -c"
SET "PSQL_TEET=psql -h localhost -U teet -c"

echo "Dropping and recreating teet_template database."
%PSQL% "DROP DATABASE IF EXISTS teet_template;"
%PSQL% "CREATE DATABASE teet_template;"
echo "Restoring teeregister dump"
%PSQL% "CREATE EXTENSION IF NOT EXISTS postgis;"
%PSQL% "DROP ROLE IF EXISTS teeregister;"
%PSQL% "CREATE ROLE teeregister;"

echo "Dropping and recreating teet database."
%PSQL% "DROP DATABASE IF EXISTS teet;"
%PSQL% "DROP ROLE IF EXISTS authenticator;"
%PSQL% "DROP ROLE IF EXISTS teet_anon;"
%PSQL% "DROP ROLE IF EXISTS teet_user;"
%PSQL% "DROP ROLE IF EXISTS teet_backend;"
%PSQL% "DROP ROLE IF EXISTS teet;"
%PSQL% "CREATE ROLE teet WITH LOGIN SUPERUSER;"
%PSQL% "CREATE DATABASE teet TEMPLATE teet_template OWNER teet;"

%PSQL% "CREATE ROLE authenticator LOGIN;"

echo "Running migrations"
mvn compile flyway:migrate

echo "Adding all privileges in schema teet to teet_anon."
%PSQL_TEET% "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA teet TO teet_anon;"

echo "Adding all privileges in schema teet to teet_anon."
%PSQL_TEET% "GRANT SELECT, UPDATE, INSERT, DELETE ON ALL TABLES IN SCHEMA teet TO teet_user;"

echo "Done! Next, start your PostgREST server and import datasources by running 'clojure -A:import example-config.edn' in ../app/datasource-import/."

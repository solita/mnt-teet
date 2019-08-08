# Flyway migrations for TEIS database

All TEIS database migrations are in the same flyway migrations.

pom.xml contains a template for running the flyway migrations.

## Create local dev db

* Install PostgreSQL with PostGIS extension locally (or run with docker)

* Run commands in psql:

> CREATE ROLE teet WITH LOGIN SUPERUSER;
> CREATE DATABASE teet OWNER teet;


* Run migrations: mvn flyway:migrate

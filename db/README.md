# Flyway migrations for TEIS database

All TEIS database migrations are in the same flyway migrations.

pom.xml contains a template for running the flyway migrations.

## Create local dev db

* Install PostgreSQL with PostGIS extension locally (or run with docker)

* Run commands in psql:

> CREATE ROLE teis WITH LOGIN SUPERUSER;
> CREATE DATABASE teis OWNER teis;

* Run migrations: mvn flyway:migrate

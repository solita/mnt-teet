# Flyway migrations for TEET database

All TEET database migrations are in the same flyway migrations.

pom.xml contains a template for running the flyway migrations.

## Create local dev db

* Install PostgreSQL with PostGIS extension locally (or run with docker)

* Run commands in psql:

> CREATE ROLE teet WITH LOGIN SUPERUSER;
> CREATE DATABASE teet OWNER teet;


* Run migrations: mvn flyway:migrate

## GIS data dump

* This is loaded from a private S3 bucket. If you don't have AWS SDK creds set up yet: clone a dev sdk user in the IAM AWS console & set up your AWS CLI creds so that you can sucessfully run es `aws s3 ls  s3://teet-dev-files`


## Maa-amet restrictions data dump

* Dump is loaded from https://geoportaal.maaamet.ee/est/Andmete-tellimine/Avaandmed/Kitsenduste-kaardi-andmed-p615.html
* Download and unzip "Kitsenduste mõjualad GPKG"
* run `ogr2ogr -f "PostgreSQL" PG:"host=localhost user=teet dbname=teet" KITSENDUSED.gpkg -lco schema=restrictions -lco spatial_index=yes` to import the restrictions tables (takes a while)
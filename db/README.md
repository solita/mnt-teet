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



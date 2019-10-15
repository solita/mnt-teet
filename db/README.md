# Flyway migrations for TEET database

All TEET database migrations are in the same flyway migrations.

pom.xml contains a template for running the flyway migrations.

## Create local dev db

* Install PostgreSQL with PostGIS extension locally (or run with docker)

* Run `devdb_clean.sh`

## GIS data dump

* This is loaded from a private S3 bucket. If you don't have AWS SDK creds set up yet: clone a dev sdk user in the IAM AWS console & set up your AWS CLI creds so that you can sucessfully run es `aws s3 ls  s3://teet-dev-files`
* NOTE that the dump is imported when running `devdb_clean.sh`!


## Maa-amet restrictions data dump

* Dump is loaded from https://geoportaal.maaamet.ee/est/Andmete-tellimine/Avaandmed/Kitsenduste-kaardi-andmed-p615.html
* Download and unzip "Kitsenduste mõjualad GPKG"
* run `ogr2ogr -f "PostgreSQL" PG:"host=localhost user=teet dbname=teet" KITSENDUSED.gpkg -lco schema=restrictions -lco spatial_index=yes` to import the restrictions tables (takes a while)
* run SELECT public.ensure_restrictions_indexes();
* NOTE that the restriction data dump is also imported when running `devdb_clean.sh`!

## Cadastral data dump

* Dump is loaded from https://geoportaal.maaamet.ee/docs/katastripiirid/paev/KATASTER_EESTI_GPKG.zip
* Download and unzip
* Dump needs to fixed with spatialite:
** `spatialite KATASTRIYKSUS.gpkg`
** `update KATASTRIYKSUS SET moodustatud=NULL WHERE moodustatud='0000-00-00';`
** `.dumpdbf KATASTRIYKSUS KATASTRIYKSUS.dbf UTF-8`
* Run `ogr2ogr -f "PostgreSQL" PG:"host=localhost user=teet dbname=teet" KATASTRIYKSUS.dbf -lco schema=cadastre`
* NOTE that cadastral data is imported when running `devdb_clean.sh`!

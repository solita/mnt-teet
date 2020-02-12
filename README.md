# Maanteeamet TEET: Road Lifecycle Software

Maanteeamet TEET project.

## Structure

* ci/  contains build and deployment scripts for AWS CodeBuild
* db/  contains migrations for setting up the database
* app/ contains different parts of the application
* app/api/ the TEET database API
* app/frontend/ TEET frontend app
* app/backend/  Backend service code (Datomic Ions)

## Localizations

The single source of truth for current localizations is in the project
Sharepoint, in `Localizations.xlsx`. The localizations are synced into
source code by downloading the sheet and running

``` shell
clj -A:localizations <path-to-downloaded-sheet>
```

in `app/build-tools`. This will update the localization `.edn` files in
`app/frontend/resources/public/language`.

## Version numbering

Version numbering for TEET is `43.yy.mm.N` where
`yy` is the last 2 digits of the release year,
`mm` is the release month and
`N` is a sequence number (always starts at 1 for a new year/month pair).

So the first version of january 2020 would be `43.20.01.1`.
Versions are made into branches named `v<version>`.

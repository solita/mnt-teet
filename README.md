# Maanteeamet TEET: Road Lifecycle Software

Maanteeamet TEET project.

## Structure

* ci/  contains build and deployment scripts for AWS CodeBuild
* db/  contains migrations for setting up the database
* app/ contains different parts of the application
* app/api/ the TEET database API
* app/frontend/ TEET frontend app
* app/backend/  Backend service code (Datomic Ions)
* app/build-tools/ Tools to synchronize external information to repository

## Version numbering

Version numbering for TEET is `43.yy.mm.N` where
`yy` is the last 2 digits of the release year,
`mm` is the release month and
`N` is a sequence number (always starts at 1 for a new year/month pair).

So the first version of january 2020 would be `43.20.01.1`.
Versions are made into branches named `v<version>`.

## Feature flags

When adding a new feature flag:

- Add the new feature flag to the local config file in the repository which contains it.
- Add info about the feature flag to Confluence, under 2_0_Environments > Feature flags.
- Ensure the feature flag is properly added to other environments via SSM parameters.

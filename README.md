# Maanteeamet TEET: Road Lifecycle Software

Maanteemat TEET project.

## Structure

* ci/  contains build and deployment scripts for AWS CodeBuild
* db/  contains migrations for setting up the database
* app/ contains different parts of the application
* app/api/ the TEET database API
* app/frontend/ TEET frontend app
* cfn/  contains all cloudformation templates
* cfn/ci/  contains templates to setup codebuild and buckets (singleton)
* cfn/db/  database cluster
* cfn/services  ECR cluster and ECS task defs

* cfn/auth  cognito user pool and TARA integration

## Datomic

TEET uses Datiomic Ion as a database.

### Connecting to Datomic locally

* Download [datomic-socks-proxy script](https://docs.datomic.com/cloud/files/datomic-socks-proxy)
  and add it to your path.
* Add the contents of  [TEET-42](https://jira.mkm.ee/browse/TEET-42) to
  `../teet-local/config.edn` relative to the TEET project root.
* Load and switch to `teet.environment`.
* Eval `(load-local-config!)`.
* Eval `(d/create-database (datomic-client) {:db-name (str (System/getProperty "user.name") "-dev")})`.
* Eval `(datomic-connection) => {:db-name "yourname-dev", :database-id "foo", ...}`

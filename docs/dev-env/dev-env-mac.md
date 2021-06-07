# Installing TEET dev environment on Mac machine - without docker.

## Installing and configuring the environment
- Install Clojure (could also be bundled with for example Leiningen). You can check if it works by executing "clj" in a terminal
- Install Node.JS / NPM
- Install Maven (needed for dev-tools installation, needs to be added to PATH)
- Install AWS CLI (https://aws.amazon.com/cli/)
- Install Datomic dev-local
  - https://cognitect.com/dev-tools
  - Just install Cognitect dev-tools (which includes REBL and Datomic dev-local) by requesting an email from cognitech. 
    The email received from cognitech has a link "maven configuration". 
    This step has to be performed (create a settings.html file in user /.m2 folder).
- Create a .datomic/dev-local.edn file in your home directory 
   (C:\Users\xxxxxx\), containing a map with :storage-dir and an absolute 
   path, i.e.: `{:storage-dir "C:/Users/xxxxxx/.datomic/"}`
- Install postgres.app for MAC (includes PostGIS extension)
- Install postgrest, e.g. `brew install postgrest`
- If you have installed the latest version of Cognitect dev-tools, make sure to update the version in backend\deps.edn file, e.g.:
   ```
   com.datomic/dev-local {:mvn/version "0.9.232"}
   ```
- Request access (ask in the team) to AWS (https://intra.solita.fi/pages/viewpage.action?pageId=62261630) 
  and update your user's ~/.aws/credentials file with following:
`
[teet-dev]
region = eu-central-1
aws_region = eu-central-1
aws_access_key_id = access-key
aws_secret_access_key = secret-key
`
   run $ aws configure if the credentials file does not exist. 
- Clone from Github both mnt-teet and mnt-teet-private so that they are under the same parent folder
- On a terminal, navigate to .\app\frontend\ and execute `npm install`.
- On a terminal, try to execute backend by navigating to that folder and executing `clj -A:dev`. 
  It should start up the REPL but might not until the next two sections are done.

## Database setup
- Skip migration `db/src/main/resources/db/migration/V17__remove_unused_rpc.sql` to avoid error.
  ```
  # replace contents with empty string
  echo "" > db/src/main/resources/db/migration/V17__remove_unused_rpc.sql
  
  # optional: ignore change
  git update-index --assume-unchanged db/src/main/resources/db/migration/V17__remove_unused_rpc.sql
  ```
  - Why? 
    Versioned migration tries to `DROP FUNCTION teet.replace_entity_ids(TEXT)`.
    However, the function does not exist at this point, because repeatable migrations are run after versioned migrations.
  - The "right" solution would be to push this change to version control, but then we would need 
    to repair checksums for existing migrations?
- Run `devdb_create_template.sh` in ./db/
- Run `devdb_clean.sh` in ./db/
- Connect and start PostgREST by running `dev2.sh` in ./app/api/
- Navigate to app/datasource-import and run `clojure -A:import example-config.edn` (it takes a while to complete).
  The process should successfully import different land registry, etc. data.
  
## Start TEET
- Run REPL from ./app/backend: `clj -A:dev`
- Run the following commands from REPL:
```
(teet.main/restart)
(make-mock-users!)
(give-admin-permission "EE12345678910")
```
- Start frontend app and login with instructions from `./app/frontend/README.md`

## What needs to be done in IDEA / front-end
IntelliJ IDEA (recommended alongside with Cursive plugin) requires a license which is acquired from IT (a ticket must be created and it takes several days to arrive). You need to do few things in order to run the front-end and back-end.
- First create a new Clojure Deps project from mnt-teet folder (Open the 
  folder without creating the project from scratch will work, too).
- If you have the folder open, right-click on app\backend folder on the navigation windows and select "New > Module". It should open you a new Clojure Deps module window, where you just need to make sure the name, directories and SDK is correct.
- Once module(s) have been created, open the "Run/Debug configuration" window (top right, from dropdown select "edit configurations") and add new configuration Clojure REPL > Local.
    1. First do it from the frontend directory, selecting options: REPL type: clojure.main; Run with IntelliJ project classpath; Parameters: dev/user.clj
    2. For the backed the options are: REPL type: nREPL; Run with Deps; Aliases: dev; Environment: AWS_REGION=eu-central-1 (might not be necessary, try without).
- Then first try to run the frontend REPL and if it is successful, then backend and if that works, run (restart).

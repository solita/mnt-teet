# TEET backend app

Backend app hosts the index page and SPA application JS.

# Environment variables

- *API_URL* The URL the SPA uses for the API, defaults to "/api"
- *AWS_DEFAULT_REGION* must be set to same as the configued s3 bucket's region (for local dev setup, eu-central-1 as of this writing)

# Running locally

- Start a repl
- Call `teet.main/restart`

See `mnt-teet/docs/dev-env/` for detailed instructions.

# Testing

[Kaocha](https://github.com/lambdaisland/kaocha) is used for running unit tests.

Running tests from command line:
```
clojure -A:test

# Watch for changes
clojure -A:test --watch

# Exit at first failure
clojure -A:test --fail-fast

# Only run the `unit` suite
clojure -A:test unit

# Only run a single test
clojure -A:test --focus my.app.foo-test/bar-test

# Use an alternative config file
clojure -A:test --config-file tests_ci.edn

# See all available options
clojure -A:test --test-help
```

## Tests with db
- Tag test namespace with `^:db`
- Apply necessary fixtures from `teet.test.utils`
- Run with `clojure -A:db-test`
- Run alongside unit tests with `clojure -A:test-all`

# Datomic

TEET uses Datiomic Ion as a database.

## Using local Datomic

New local dev datomic does not need network connection, but stores
data locally on disk. It is now used for CI tests as well.

- Download tools https://cognitect.com/dev-tools
- Create file `~/.datomic/dev-local.edn` with content `{:storage-dir "/Some/Directory"}`

## Importing cloud database for local development

The local database is initially quite empty. Follow these steps to import the database from cloud
test environment for local development use.

### Download and install Datomic CLI Tools

- Download [Datomic CLI Tools](https://docs.datomic.com/cloud/operation/cli-tools.html)
- Unzip datomic-cli.zip to installation folder of choice
- `cd datomic-cli`
- See `datomic-cli/README`
- Make each of the scripts executable: `chmod +x datomic*`
- Optional: add `datomic-cli` to PATH for convenience

### Open SSH tunnel to Datomic compute group in AWS

 - In terminal, run `datomic client access teet-datomic`
 - Answer yes to prompt "Are you sure you want to continue connecting (yes/no/[fingerprint])?"
 - You should see something like: "Authentication succeeded (publickey)."

### Run import function in backend REPL

- Run function `(user/import-current-cloud-dbs)` 
- You should see a prompt like this:
```
Importing databases from cloud:
CLOUD:  teet20210609  => LOCAL:  teet20210609
CLOUD:  teetasset20210609  => LOCAL:  asset20210609  
Press enter to continue or abort evaluation.
```
- Input anything into the REPL to continue
- If you get a connection error on the first try, just try again
- The import should take a few minutes
```
Importing TEET db
Importing...............................................
Importing asset db
Importing.................................................
Done. Remember to change config.edn to use new databases.
```

### Take new databases to use 

- Edit `mnt-teet-private/config.edn`
- Copy the database names from the above REPL output, for example:
```
:db-name "teet20210609"
:asset-db-name "asset20210609" 
```
- Restart REPL
- In REPL: `(teet.main/restart)`
- Check that you can see projects in the [Project list](http://localhost:4000/#/projects/list)

### Update geographic data in Postgres

The projects under project listing do not yet appear on the 
[Project map](http://localhost:4000/#/projects/map). 
Here is how to update Postgres geographic data to match projects in Datomic:
- In REPL, run function `(user/update-project-geometries!)`

## Troubleshooting

- If you a build error about fetching datomic.ion dep like "Error building classpath. Could not find artifact com.datomic:ion:jar:0.9.35 in central (https://repo1.maven.org/maven2/)" it means, contrary to the message, that it's failing to get it from the Datomic s3 bucket source, check your AWS creds (eg awscli and Java AWS sdk have incompatibilities in parsing ~/.aws configs)
- If you get this at the REPL: "Execution error (ExceptionInfo) at datomic.client.api.async/ares (async.clj:58).
Forbidden to read keyfile at s3://[...]/dbs/catalog/read/.keys. Make sure that your endpoint is correct, and that your ambient AWS credentials allow you to GetObject on the keyfile." and aws s3 ls still works, it can be the same incomatibility as the first case, or you might have the wrong aws profile active.

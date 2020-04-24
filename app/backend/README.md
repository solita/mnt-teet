# TEET backend app

Backend app hosts the index page and SPA application JS.

# Environment variables

- *API_URL* The URL the SPA uses for the API, defaults to "/api"

# Running locally

- Start a repl
- Call `teet.main/restart`


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

## Connecting to Datomic locally

- Set `AWS_PROFILE` to your TEET profile
- Download [datomic-socks-proxy script](https://docs.datomic.com/cloud/files/datomic-socks-proxy)
  and add it to your path or invoke it by absolute path.
- Run `datomic-socks-proxy teet-dev-datomic`
- Ensure you have
  [mnt-teet-private](https://github.com/solita/mnt-teet-private)
  repository checked out as a sibling directory to the TEET project.
- Load and switch to `teet.environment`.
- Eval `(load-local-config!)`.
- Eval only when setting up `(user/create-db "yourname-dev")`.
- Eval `(datomic-connection) => {:db-name "yourname-dev", :database-id "foo", ...}`
- import THK data: Find the latest thk->teet .csv file from the dev S3 bucket and call (user/import-thk-from-localfile <path>)
- When creating your dev datomic db for the first time, run (make-mock-users!) and give permissions to some users with eg (give-manager-permission [:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"]) (this makes Danny D. Manager admin)

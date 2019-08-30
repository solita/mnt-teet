# TEET backend app

Backend app hosts the index page and SPA application JS.

# Environment variables

- *API_URL* The URL the SPA uses for the API, defaults to "/api"

# Running locally

- Start a repl
- Call `teet.main/restart`


# Datomic

TEET uses Datiomic Ion as a database.

## Connecting to Datomic locally

- Set `AWS_PROFILE` to your TEET profile
- Download [datomic-socks-proxy script](https://docs.datomic.com/cloud/files/datomic-socks-proxy)
  and add it to your path.
- Run `datomic-socks-proxy teet-dev-datomic`
- Ensure you have
  [mnt-teet-private](https://github.com/solita/mnt-teet-private)
  repository checked out as a sibling directory to the TEET project.
- Load and switch to `teet.environment`.
- Eval `(load-local-config!)`.
- Eval `(d/create-database (datomic-client) {:db-name (str (System/getProperty "user.name") "-dev")})`.
- Eval `(datomic-connection) => {:db-name "yourname-dev", :database-id "foo", ...}`

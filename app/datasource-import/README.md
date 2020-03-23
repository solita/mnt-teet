# TEET datasource import

A small command line app used for importing relevant datasources, such
as restrictions and cadastral units, to the TEET Postgres
database. The application inserts the data using the PostgREST API, so
make sure that you have PostgREST running.

## Usage

```
clj -A:import <config file> [<datasource id>]
```

The example config file should work given that the dev environment
PostgREST was set up with the default credentials. Datasource ids can
be found in the `teet.datasource` table. If no id is provided, the
import is run for all ids present in the table.

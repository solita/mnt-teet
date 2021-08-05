# Build tools

Build tools contains tools that are needed to synchronize external data that is defined in shared
spreadsheets. Download the spreadsheet and run the tool to update.

## Localization

The single source of truth for current localizations is in the project
Sharepoint, in `Localizations.xlsx`. The localizations are synced into
source code by downloading the sheet and running

``` shell
clj -A:localizations <path-to-downloaded-sheet>
```

in this directory. This will update the localization `.edn` files in
`app/frontend/resources/public/language`.

## Authorizations

The authorization matrix is updated in Sharepoing `Authorizations.xlsx`.

To update, run:

```shell
clj -A:authorizations <path-to-downloaded-sheet>
```

in this directory. This will update the `authorization.edn` in
`app/backend/resources`.

## Contract authorizations

The contract authorization is similar to the "regular" authorization matrix in that it's a page from the same sharepoint

To update the contract matrix, run:

```shell
clj -A:contract-authorizations <path-to-the-downloaded-sheet>
```

In this directory. This will update the `contract-authorization.edn` in the `app/backend/resources`


## Asset database schema

Asset database schema is taken from `TEET_ROTL.xlsx` file in Sharepoint.
Download the sheet and run:

```shell
clj -A:asset-schema <path-to-downloaded-sheet>
```

in this directory. This will update the `asset-schema.edn` file in
`app/backend/resources`.

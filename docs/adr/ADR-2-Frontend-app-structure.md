# ADR-2 Namespace structure

## Context

TEET is a large web application and will contain many namespaces and different types of namespaces:
views, controllers, common UI components, etc.

The way to structure namespaces affects how easy it is to find and refer to a given piece of code.
Common structure makes it more predictable where developers can expect to find things.

## Decision

We use feature based grouping with layer suffix.

`teet.<feature>.<feature>-<layer>`

Example: `teet.search.search-view` and `teet.search.search-controller`

When referring to namespaces, use the last part of the name fully, e.g. `search-view`
```clojure
(ns teet.search.search-view
  (:require [teet.search.search-controller :as search-controller]))```

Features may use implementation specific sub-namespaces as seen fit.

Non-feature functionality, like common UI utilities, are placed under the layer, e.g. `teet.ui.panels`.

## Status

Accepted

## Consequences

Code related to the same functionality can usually be found in the same folder. Feature name
is duplicated in the last part of the namespace name because we don't want to have many files
in the source folders with the same name (like `view.cljs`).

- Pro: Uniform style makes it easy to find related code
- Con: cross-cutting concerns not related to a specific user facing feature need special consideration when naming
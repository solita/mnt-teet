# ADR-3 Database and data storage technologies

## Context

There are various requirements for the database containing master data
that TEET is authoritative for.  These include ACID properties
(Atomicity, Consistency, isolation, Durability), preservation of
history, extensibility, GIS data support, file attachments.

## Decision

The master database containing business data, workflows, etc is
Datomic, as the data model providing history preservation and data
model extensibility is a good fit for TEET business requirements
identified so far. In addition the AWS-native deployment and operating
model and and excellent fit with Clojure data structure paradigms are
significant pluses.

PostgreSQL + PostGIS and AWS S3 are used as auxiliary databases for
imported GIS data (that we are not the master of) and file
attachments.  PostgreSQL (as provided by AWS RDS) is used via
PostgREST to provide an API for map layer and geometry data.

PostgreSQL will also be used as our full text search engine by caching
full-text data set in it.

## Status

Accepted

## Consequences

 - Use of auxiliary databases will sometimes require more than one
   round trip to fetch data cross referenced between databases.

 - Auxiliary databases can always be overwritten with data from the
   master database. Writes involving both master and auxiliary
   databases are committed to master before write operations to
   auxiliary databases. Failures of an auxiliary database does not
   roll back master after a DB transaction. An option is to explore
   two-phase commit support in our databases to implement robust
   cross-DB transactions.

 - Database information model is the same as in our application code
   and does not require mapping. This improves development speed.

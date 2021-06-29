-- Add table for asset/component geometries

CREATE TABLE teet.asset (
 oid TEXT PRIMARY KEY,
 geometry GEOMETRY
);

CREATE INDEX asset_geometry_idx ON teet.asset USING GIST (geometry);

GRANT ALL PRIVILEGES ON TABLE teet.asset TO teet_backend;

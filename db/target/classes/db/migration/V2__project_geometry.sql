-- Add geometry column to THK project and a spatial index for it

ALTER TABLE teet.thk_project
  ADD COLUMN geometry geometry(Geometry,3301);

CREATE INDEX thk_project_geom_idx ON teet.thk_project USING GIST (geometry);


-- Create schema for implementation specific functions (not published in API)

CREATE SCHEMA impl;

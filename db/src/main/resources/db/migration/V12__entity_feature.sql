-- Geometries extracted from entities (like survey .ags files)

CREATE TABLE teet.entity_feature (
 id TEXT, -- feature id within the entity
 entity_id BIGINT, -- :db/id of entity (doesn't need to exist in entity table)
 type TEXT, -- Some feature type (internal to datasource)
 label TEXT, -- Label to be shown on map (as tooltip)
 geometry geometry(Geometry,3301),
 properties JSONB,

 PRIMARY KEY(entity_id, id)
);

CREATE INDEX entity_feature_geom_idx ON teet.entity_feature USING GIST (geometry);

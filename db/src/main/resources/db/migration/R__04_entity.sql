-- Create functions for fetching entity MVT and GeoJSON
CREATE OR REPLACE FUNCTION teet.mvt_entities(ids BIGINT[],
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT e.tooltip,
             e.id, e.type,
             ST_AsMVTGeom(e.geometry,
                          ST_SetSRID(ST_MakeBox2D(ST_MakePoint($2, ymin),
                                                  ST_MakePoint($4, ymax)), 3301),
                          4096, NULL, false)
      FROM teet.entity e
      WHERE e.id = ANY(ids)) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.mvt_entities(type entity_type,
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT e.tooltip,
             e.id, e.type,
             ST_AsMVTGeom(e.geometry,
                          ST_SetSRID(ST_MakeBox2D(ST_MakePoint($2, ymin),
                                                  ST_MakePoint($4, ymax)), 3301),
                          4096, NULL, false)
      FROM teet.entity e
      WHERE e.type = type AND e.geometry IS NOT NULL AND
            ST_DWITHIN(e.geometry,
                ST_SetSRID(ST_MakeBox2D(ST_MakePoint($2, ymin),
                                        ST_MakePoint($4, ymax)), 3301),
            300)) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE FUNCTION teet.geojson_entities(ids BIGINT[])
    RETURNS TEXT
AS $$
SELECT row_to_json(fc)::TEXT
FROM (SELECT 'FeatureCollection' as type,
             array_to_json(array_agg(f)) AS features
      FROM (SELECT 'Feature' as type,
                   ST_AsGeoJSON(e.geometry)::json AS geometry,
                   json_build_object('id', e.id::text,
                                     'tooltip', e.tooltip,
                                     'type', e.type) AS properties
            FROM teet.entity e
            WHERE e.id = ANY(ids)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE FUNCTION teet.geojson_entities(type entity_type)
    RETURNS TEXT
AS $$
SELECT row_to_json(fc)::TEXT
FROM (SELECT 'FeatureCollection' as type,
             array_to_json(array_agg(f)) AS features
      FROM (SELECT 'Feature' as type,
                   ST_AsGeoJSON(e.geometry)::json AS geometry,
                   json_build_object('id', e.id::text,
                                     'tooltip', e.tooltip,
                                     'type', e.type) AS properties
            FROM teet.entity e
            WHERE e.type = type AND e.geometry IS NOT NULL) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_entity_pins(type entity_type)
    RETURNS TEXT
AS $$
SELECT row_to_json(fc)::TEXT
FROM (SELECT 'FeatureCollection' as type,
             array_to_json(array_agg(f)) AS features
      FROM (SELECT 'Feature' as type,
                   ST_AsGeoJSON(st_centroid(e.geometry))::json AS geometry,
                   json_build_object('id', e.id::text,
                                     'tooltip', e.tooltip) AS properties
            FROM teet.entity e
            WHERE e.type = type AND e.geometry IS NOT NULL AND NOT ST_isempty(e.geometry)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;


GRANT EXECUTE ON FUNCTION teet.geojson_entity_pins TO teet_user;

GRANT EXECUTE ON FUNCTION teet.mvt_entities TO teet_user;
GRANT EXECUTE ON FUNCTION teet.mvt_entities TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.geojson_entities TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entities TO teet_backend;

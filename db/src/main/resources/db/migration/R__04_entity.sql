-- Create functions for fetching entity MVT and GeoJSON
CREATE OR REPLACE FUNCTION teet.mvt_entities(ids BIGINT[],
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT e.tooltip,
             e.id::TEXT, e.type,
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
             e.id::TEXT, e.type,
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

CREATE OR REPLACE FUNCTION teet.geojson_entities(ids BIGINT[])
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

CREATE OR REPLACE FUNCTION teet.geojson_entities(type entity_type)
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

CREATE OR REPLACE FUNCTION teet.geojson_entity_pins(ids BIGINT[])
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
            WHERE e.id = ANY(ids) AND e.geometry IS NOT NULL AND NOT ST_isempty(e.geometry)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;




CREATE OR REPLACE FUNCTION teet.geojson_entity_cluster_pins(type entity_type, cluster_distance NUMERIC)
RETURNS TEXT
AS $$
WITH clusters AS (
     SELECT unnest(st_clusterwithin(e.geometry, cluster_distance)) geom
     FROM teet.entity e
     WHERE NOT ST_IsEmpty(e.geometry) AND e.type = type
)
SELECT row_to_json(fc)::TEXT
FROM (SELECT 'FeatureCollection' as type,
             array_to_json(array_agg(f)) AS features
      FROM (SELECT 'Feature' as type,
                   ST_AsGeoJSON(st_centroid(c.geom))::json AS geometry,
                   json_build_object('tooltip', ST_NumGeometries(c.geom)::TEXT)
           AS properties
           FROM clusters c) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;


GRANT EXECUTE ON FUNCTION teet.geojson_entity_pins(BIGINT[]) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entity_pins(type entity_type) TO teet_user;

GRANT EXECUTE ON FUNCTION teet.mvt_entities(BIGINT[], NUMERIC, NUMERIC, NUMERIC, NUMERIC) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.mvt_entities(type entity_type, NUMERIC, NUMERIC, NUMERIC, NUMERIC) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entities(BIGINT[]) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entities(type entity_type) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entity_cluster_pins(type entity_type, NUMERIC) TO teet_user;


CREATE OR REPLACE FUNCTION teet.store_entity_info(id TEXT, type entity_type, tooltip TEXT, geometry_wkt TEXT)
RETURNS BIGINT
AS $$
INSERT
  INTO teet.entity
       (id, type, tooltip, geometry)
VALUES (id::BIGINT, type, tooltip, ST_SetSRID(ST_GeomFromText(geometry_wkt), 3301))
ON CONFLICT (id) DO
UPDATE SET tooltip = EXCLUDED.tooltip,
           geometry = ST_SetSRID(ST_GeomFromText(geometry_wkt), 3301)
RETURNING id;
$$ LANGUAGE SQL SECURITY DEFINER;

GRANT teet_backend TO authenticator;
GRANT USAGE ON SCHEMA teet TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.store_entity_info(text,entity_type,text,text) TO teet_backend;

CREATE OR REPLACE FUNCTION teet.gml_entity_search_area(entity_id BIGINT, distance INTEGER)
RETURNS TEXT
AS $$
SELECT ST_AsGML(ST_FlipCoordinates(ST_Collect(x.geometry)))
  FROM (SELECT geometry
          FROM teet.entity_feature ef
         WHERE ef.entity_id=$1 AND type = 'search-area'
         UNION
        SELECT ST_Buffer(e.geometry, $2)
          FROM teet.entity e
         WHERE e.id=$1) x;
$$ LANGUAGE SQL SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION teet.gml_entity_search_area(BIGINT,INTEGER) TO teet_backend;

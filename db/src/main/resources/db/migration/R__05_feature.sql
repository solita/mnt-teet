-- Functions to fetch datasource features as map layers

CREATE OR REPLACE FUNCTION teet.mvt_features(datasource INT, types TEXT[],
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT f.label AS tooltip,
             f.id, f.type,
             datasource AS datasource,
             ST_AsMVTGeom(f.geometry,
                          ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                                  ST_MakePoint($5, ymax)), 3301),
                          4096, NULL, false)
       FROM teet.feature f
      WHERE ST_DWithin(f.geometry,
                       ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                               ST_MakePoint($5, ymax)), 3301),
                       1000)
        AND (array_length(types,1) IS NULL OR f.type = ANY(types))) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_entity_related_features(
  entity_id BIGINT,
  datasource_ids INT[],
  distance INTEGER)
RETURNS TEXT
AS $$
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(f.geometry)::json as geometry,
                       f.properties
                  FROM teet.feature f
                  JOIN teet.entity e ON ST_DWithin(f.geometry, e.geometry, distance)
                 WHERE e.id = entity_id
                   AND f.datasource_id = ANY(datasource_ids)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.datasources () RETURNS JSON
AS $$
SELECT json_agg(row_to_json(r))
  FROM (SELECT id,name,description,id_pattern,label_pattern
          FROM teet.datasource) r;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION teet.mvt_features(INT,TEXT[],NUMERIC,NUMERIC,NUMERIC,NUMERIC) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entity_related_features(BIGINT,INT[],INTEGER) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.datasources() TO teet_user;

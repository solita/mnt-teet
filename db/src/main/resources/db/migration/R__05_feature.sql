-- Functions to fetch datasource/entity features as map layers

CREATE OR REPLACE FUNCTION teet.mvt_features(datasource INT, types TEXT[],
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT f.label AS tooltip,
             f.id, f.type,
             datasource AS datasource,
             CONCAT(f.datasource_id,':',f.id) AS "teet-id",
             ST_AsMVTGeom(f.geometry,
                          ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                                  ST_MakePoint($5, ymax)), 3301),
                          4096, NULL, false)
       FROM teet.feature f
      WHERE ST_DWithin(f.geometry,
                       ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                               ST_MakePoint($5, ymax)), 3301),
                       1000)
        AND f.datasource_id = datasource
        AND (array_length(types,1) IS NULL OR f.type = ANY(types))) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.mvt_entity_features(entity TEXT, types TEXT[],
                                                    xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT f.label AS tooltip,
             f.id, f.type,
             entity AS entity,
             CONCAT(f.entity_id,':',f.id) AS "teet-id",
             ST_AsMVTGeom(f.geometry,
                          ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                                  ST_MakePoint($5, ymax)), 3301),
                          4096, NULL, false)
       FROM teet.entity_feature f
      WHERE ST_DWithin(f.geometry,
                       ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                               ST_MakePoint($5, ymax)), 3301),
                       1000)
        AND f.entity_id = entity::BIGINT
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
                       (jsonb_build_object('teet-id', CONCAT(f.datasource_id,':',f.id))
		        || (jsonb_build_object('deleted', f.deleted))
		        || f.properties) AS properties
                  FROM teet.feature f
                  JOIN teet.entity e ON ST_DWithin(f.geometry, e.geometry, distance)
                 WHERE e.id = entity_id
                   AND f.datasource_id = ANY(datasource_ids)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_related_features_for_entity_by_type(
    datasource_ids INT[],
    entity_id bigint,
    type text)
RETURNS TEXT AS $$
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(f.geometry)::json as geometry,
                       (jsonb_build_object('teet-id', CONCAT(f.datasource_id,':',f.id))
		        || (jsonb_build_object('deleted', f.deleted))
		        || f.properties) AS properties
                  FROM teet.feature f
                  JOIN teet.entity_feature e ON ST_DWithin(e.geometry, f.geometry, 0)
                 WHERE e.entity_id = $2 AND e.type = $3
                   AND f.datasource_id = ANY(datasource_ids)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_entity_features_by_type(
    entity_id bigint,
    type text)
RETURNS TEXT AS $$
SELECT row_to_json(fc)::text
    FROM (SELECT 'FeatureCollection' as type,
                 array_to_json(array_agg(ef)) as features
            FROM (SELECT 'Feature' as type,
                         ST_AsGeoJSON(ef.geometry)::json as geometry,
                         (jsonb_build_object('label', ef.label, 'id', ef.id)) AS properties
                    FROM teet.entity_feature ef
                    WHERE ef.entity_id = $1 AND ef.type = $2) ef) fc
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_features_within_area(
   datasource_ids INT[],
   geometry_wkt TEXT,
   distance INTEGER)
RETURNS TEXT AS $$
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(f.geometry)::json as geometry,
                       (jsonb_build_object('teet-id', CONCAT(f.datasource_id,':',f.id))
		        || (jsonb_build_object('deleted', f.deleted))
		        || f.properties) AS properties
                  FROM teet.feature f
                 WHERE ST_DWithin(f.geometry, ST_SetSRID(ST_GeomFromText(geometry_wkt),3301), distance)
                   AND f.datasource_id = ANY(datasource_ids)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_features_by_id(
  ids TEXT[])
RETURNS TEXT
AS $$
WITH features AS (
  SELECT left(x.id, position(':' in x.id) - 1)::integer as datasource,
         right(x.id, -position(':' in x.id)) as id
    FROM unnest(ids) x (id)
)
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(f.geometry)::json as geometry,
                       (jsonb_build_object('teet-id', CONCAT(f.datasource_id,':',f.id))
		        || (jsonb_build_object('deleted', f.deleted))
			|| f.properties) AS properties
                  FROM teet.feature f
                  JOIN features fs ON (f.datasource_id = fs.datasource AND f.id = fs.id)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_entity_features_by_id(
  ids TEXT[])
RETURNS TEXT
AS $$
WITH features AS (
  SELECT left(x.id, position(':' in x.id) - 1)::bigint as entity,
         right(x.id, -position(':' in x.id)) as id
    FROM unnest(ids) x (id)
)
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(f.geometry)::json as geometry,
                       (jsonb_build_object('teet-id', CONCAT(f.entity_id,':',f.id))
		        || f.properties) AS properties
                  FROM teet.entity_feature f
                  JOIN features fs ON (f.entity_id = fs.entity AND f.id = fs.id)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.datasources () RETURNS JSON
AS $$
SELECT json_agg(row_to_json(r))
  FROM (SELECT id,name,description,id_pattern,label_pattern
          FROM teet.datasource) r;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.upsert_entity_feature
   (entity TEXT, id TEXT, geometry TEXT, type TEXT, label TEXT, properties JSONB)
RETURNS TEXT
AS $$
INSERT INTO teet.entity_feature (entity_id, id, geometry, type, label, properties)
VALUES ($1::BIGINT, $2, ST_SetSRID(ST_GeomFromText($3),3301), $4, $5, $6)
ON CONFLICT (entity_id,id) DO UPDATE
   SET geometry = EXCLUDED.geometry,
       type = EXCLUDED.type,
       label = EXCLUDED.label,
       properties = EXCLUDED.properties
RETURNING CONCAT(entity_id,':',id);
$$ LANGUAGE SQL SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.select_feature_properties(ids TEXT[], properties TEXT[])
RETURNS JSON AS $$
WITH features AS (
  SELECT left(x.id, position(':' in x.id) - 1)::integer as datasource,
         right(x.id, -position(':' in x.id)) as id
    FROM unnest(ids) x (id)
)
SELECT json_object_agg(
          -- Feature id as key
          CONCAT(f.datasource_id,':',f.id),
          -- JSON object of selected properties as value
          (SELECT json_object_agg(x.prop, f.properties->x.prop)
             FROM unnest($2) x(prop)))
  FROM teet.feature f
  JOIN features fs ON (fs.datasource = f.datasource_id AND fs.id = f.id)
$$ LANGUAGE SQL;


GRANT EXECUTE ON FUNCTION teet.mvt_features(INT,TEXT[],NUMERIC,NUMERIC,NUMERIC,NUMERIC) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.mvt_entity_features(TEXT,TEXT[],NUMERIC,NUMERIC,NUMERIC,NUMERIC) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entity_related_features(BIGINT,INT[],INTEGER) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_features_by_id(TEXT[]) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.datasources() TO teet_user;
GRANT EXECUTE ON FUNCTION teet.upsert_entity_feature(TEXT,TEXT,TEXT,TEXT,TEXT,JSONB) TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.geojson_entity_features_by_id(TEXT[]) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_features_within_area(INT[],TEXT,INTEGER) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_related_features_for_entity_by_type(INT[], BIGINT, TEXT) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entity_features_by_type(bigint, TEXT) TO teet_user;
GRANT EXECUTE ON FUNCTION teet.select_feature_properties(TEXT[],TEXT[]) TO teet_backend;
GRANT ALL ON TABLE teet.entity_feature TO teet_backend;

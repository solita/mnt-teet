-- name: fetch-region-ds
-- Fetch the ids of datasources that contain regions
SELECT id FROM teet.datasource WHERE name IN ('counties','municipalities');

-- name: fetch-regions
SELECT CONCAT(f.datasource_id,':',f.id) as id, f.label, f.properties->>'MKOOD' as mkood, f.properties->>'OKOOD' as okood
  FROM teet.feature f
 WHERE f.datasource_id IN (:ds);


-- name: fetch-regions-geojson-by-ids
-- single?: true
WITH features AS (
  SELECT left(x.id, position(':' in x.id) - 1)::integer as datasource,
         right(x.id, -position(':' in x.id)) as id
    FROM unnest(:ids::TEXT[]) x (id)
)
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(f.geometry)::json as geometry,
                       (jsonb_build_object('teet-id', CONCAT(f.datasource_id,':',f.id))
		        || (jsonb_build_object('deleted', f.deleted))
                        || (jsonb_build_object('tooltip', f.label))
			|| f.properties) AS properties
                  FROM teet.feature f
                  JOIN features fs ON (f.datasource_id = fs.datasource AND f.id = fs.id)) f) fc;

-- name: fetch-feature-geometry-by-datasource-and-id
-- single?: true
SELECT f.geometry
  FROM teet.feature f
 WHERE f.datasource_id = :datasource
   AND f.id = :id;

-- name: fetch-assets-intersecting-geometry
-- Find any asset OIDs where the geometry is within the given geometry.
SELECT a.oid FROM teet.asset a
 WHERE ST_Intersects(a.geometry, :geometry)
   AND LENGTH(a.oid) = 14;

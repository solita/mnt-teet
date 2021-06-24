-- name: fetch-regions
SELECT CONCAT(f.datasource_id,':',f.id) as id, f.label, f.properties->>'MKOOD' as mkood, f.properties->>'OKOOD' as okood
  FROM teet.feature f
 WHERE f.datasource_id = (select id from teet.datasource where name = 'counties')
UNION
SELECT CONCAT(f.datasource_id,':',f.id) as id, f.label, f.properties->>'MKOOD' as mkood, f.properties->>'OKOOD' as okood
  FROM teet.feature f
 WHERE f.datasource_id = (select id from teet.datasource where name = 'municipalities');


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
			|| f.properties) AS properties
                  FROM teet.feature f
                  JOIN features fs ON (f.datasource_id = fs.datasource AND f.id = fs.id)) f) fc;

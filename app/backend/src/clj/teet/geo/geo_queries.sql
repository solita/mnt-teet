-- name: geojson-entity-pins-for-type
-- single?: true
SELECT teet.geojson_entity_pins(:type::entity_type);

-- name: geojson-entity-pins-for-ids
-- single?: true
SELECT teet.geojson_entity_pins(:ids::bigint[]);

-- name: geojson-entities
-- single?: true
SELECT teet.geojson_entities(:ids::bigint[]);

-- name: mvt-entities
-- single?: true
SELECT teet.mvt_entities(:type::entity_type, :xmin::NUMERIC, :ymin::NUMERIC, :xmax::NUMERIC, :ymax::NUMERIC);

-- name: geojson-features-by-id
-- single?: true
SELECT teet.geojson_features_by_id(:ids::TEXT[]);

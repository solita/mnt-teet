-- name: geojson-entity-pins
-- single?: true
SELECT teet.geojson_entity_pins(:type::entity_type);

-- name: geojson-entities
-- single?: true
SELECT teet.geojson_entities(:ids::bigint[]);

-- name: mvt-entities
-- single?: true
SELECT teet.mvt_entities(:type::entity_type, :xmin::NUMERIC, :ymin::NUMERIC, :xmax::NUMERIC, :ymax::NUMERIC);

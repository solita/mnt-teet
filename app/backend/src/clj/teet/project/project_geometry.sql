-- name: store-entity-info!
SELECT teet.store_entity_info (:id::TEXT, :type::entity_type, :tooltip::TEXT, :geometry::TEXT);

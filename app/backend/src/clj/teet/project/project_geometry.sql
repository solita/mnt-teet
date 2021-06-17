-- name: store-entity-info!
SELECT teet.store_entity_info (:id::TEXT, :type::entity_type, :tooltip::TEXT, :geometry::TEXT);

-- name: delete-stale-projects!
-- Delete any projects that don't appear in the list
DELETE
  FROM teet.entity
 WHERE type='project'
   AND id NOT IN (:project-ids);

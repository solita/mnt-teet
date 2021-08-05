-- name: delete-geometries!
DELETE FROM teet.asset WHERE oid IN (:oids);

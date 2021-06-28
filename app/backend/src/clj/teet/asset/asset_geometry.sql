-- name: store-geometry!
-- Store a single OID/geometry pair. Updates if the OID already exists.
INSERT INTO teet.asset (oid, geometry)
VALUES (:oid, ST_SetSRID(ST_GeomFromText(:geometry), 3301))
ON CONFLICT (oid) DO
UPDATE SET geometry = ST_SetSRID(ST_GeomFromText(:geometry), 3301);

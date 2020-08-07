-- Iteration order is bad, so let's hash the geometry as id
UPDATE teet.datasource
   SET id_pattern = '{KPO_VID}-{sha256:geometry}'
 WHERE id_pattern = '{i}-{KPO_VID}';

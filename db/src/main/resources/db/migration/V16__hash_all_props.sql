-- There are duplicates still in some, hash all properties to be safe
UPDATE teet.datasource
   SET id_pattern = '{sha256:*}'
 WHERE id_pattern = '{KPO_VID}-{sha256:geometry}';

-- datasource-import now first queries the db for the existing IDs and sets deleted for any feature ID that doesn't appear in the latest imported shp file

ALTER TABLE teet.feature
  ADD COLUMN deleted BOOLEAN
  DEFAULT FALSE;


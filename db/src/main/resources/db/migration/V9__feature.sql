-- Generic feature table

CREATE TABLE teet.datasource (
  id SERIAL PRIMARY KEY,
  name TEXT UNIQUE NOT NULL,
  description TEXT,
  url TEXT, -- URL to download features from
  content_type TEXT, --  Datasource content type (like 'SHP')
  id_pattern TEXT, -- Feature id pattern, may contain mustache refs to properties
  label_pattern TEXT, -- Feature label pattern, may contain mustache references to properties
  last_import_ts TIMESTAMPTZ, -- Timestamp of last import
  last_import_hash TEXT -- SHA-256 content hash of last import

);

CREATE TABLE teet.feature (
 id TEXT, -- feature id within a datasource
 datasource_id INT REFERENCES teet.datasource (id),
 type TEXT, -- Some feature type (internal to datasource)
 label TEXT, -- Label to be shown on map (as tooltip)
 geometry geometry(Geometry,3301),
 properties JSON,

 PRIMARY KEY(datasource_id, id)
);

CREATE INDEX feature_geom_idx ON teet.feature USING GIST (geometry);

INSERT INTO teet.datasource (name, description, url, content_type, id_pattern, label_pattern)
VALUES ('survey',
        'Maa-amet survey data',
        'https://geoportaal.maaamet.ee/docs/geoloogia/andmed/Ehitusgeoloogia_uuringualad_shp.zip',
        'SHP',
        '{ID}', '{NIMI}');

GRANT SELECT ON teet.datasource TO teet_backend;

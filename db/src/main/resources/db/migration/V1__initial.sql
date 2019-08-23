-- Create users for API
CREATE ROLE teet_user NOLOGIN;
CREATE ROLE teet_anon NOLOGIN;
GRANT teet_user TO authenticator;
GRANT teet_anon TO authenticator;

CREATE SCHEMA teet;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS postgis;


CREATE TYPE teet.localized_text AS (
  lang TEXT, -- language code
  text TEXT
);


CREATE TABLE teet.thk_project (
  id TEXT PRIMARY KEY, -- Project id in THK
  plan_group_fk TEXT,

  -- Road object this project works on
  road_nr INTEGER, -- Road number (1-99999)
  bridge_nr INTEGER,
  km_range NUMRANGE, -- Start/end position kilometers from start of road
  carriageway INTEGER, -- 1 / 2

  name TEXT, -- Name of the project

  oper_method TEXT,
  object_type_fk TEXT,

  region_fk INTEGER, -- region id
  county_fk INTEGER, -- county id

  customer_unit TEXT, -- name of customer unit

  updated TIMESTAMPTZ, -- date/time of last update

  procurement_no TEXT, -- procurement "number"
  procurement_id INTEGER,

  activity_id INTEGER,
  activity_type_fk TEXT,

  -- Duration of project
  estimated_duration DATERANGE, -- estimated start/end of project

  created timestamptz NOT NULL DEFAULT now() -- when this was created in TEET database
);

-- Create trigram index on expression containing all text we want to search from
CREATE INDEX thk_project_search_idx
    ON teet.thk_project
 USING gin ((id||name||road_nr||procurement_no) gin_trgm_ops);

CREATE VIEW teet.thk_project_search AS
SELECT *, (id||name||road_nr||procurement_no) AS searchable_text
  FROM teet.thk_project;



CREATE TYPE teet.authinfo AS (
  authenticated bool,
  username text,
  email text
);

CREATE FUNCTION teet.user_info() RETURNS teet.authinfo
AS $$
BEGIN
 IF current_user = 'teet_anon' THEN
   RETURN ROW(false, NULL, NULL)::teet.authinfo;
 ELSE
   RETURN ROW(true,
              current_setting('request.jwt.claim.cognito:username'),
              current_setting('request.jwt.claim.email'))::teet.authinfo;
 END IF;
END;
$$ LANGUAGE plpgsql STABLE;

GRANT USAGE ON SCHEMA teet to teet_anon;
GRANT USAGE ON SCHEMA teet to teet_user;

GRANT SELECT ON teet.thk_project TO teet_user;

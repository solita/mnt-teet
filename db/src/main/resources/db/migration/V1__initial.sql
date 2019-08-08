-- Create users for API

CREATE ROLE authenticator LOGIN;
CREATE ROLE teet_user NOLOGIN;
CREATE ROLE teet_anon NOLOGIN;
GRANT teet_user TO authenticator;
GRANT teet_anon TO authenticator;

CREATE SCHEMA projects;
CREATE SCHEMA common;
CREATE SCHEMA users;

CREATE EXTENSION postgis;

CREATE TYPE common.fairway_type AS ENUM ('road','rail','water');

CREATE TYPE common.localized_text AS (
  lang TEXT, -- language code
  text TEXT
);

CREATE TABLE projects.project_state (
 id SERIAL PRIMARY KEY,
 name common.localized_text[],
 validity daterange
);

INSERT INTO projects.project_state (name) VALUES
 ('{"(fi,Ehdolla)","(en,Proposed)"}'::common.localized_text[]),
 ('{"(fi,Valmisteilla)","(en,In preparation)"}'::common.localized_text[]),
 ('{"(fi,Käynnissä)","(en,Started)"}'::common.localized_text[]),
 ('{"(fi,Keskeytetty)","(en,Halted)"}'::common.localized_text[]),
 ('{"(fi,Tarkastuksessa)","(en,In review)"}'::common.localized_text[]),
 ('{"(fi,Valmis)","(en,Finished)"}'::common.localized_text[]),
 ('{"(fi,Hyväksytty)","(en,Approved)"}'::common.localized_text[]),
 ('{"(fi,Muu)","(en,Other)"}'::common.localized_text[]);

CREATE TABLE projects.projectgroup_phase (
 id SERIAL PRIMARY KEY,
 name common.localized_text[],
 validity daterange
);

INSERT INTO projects.projectgroup_phase (name) VALUES
 ('{"(fi,Suunnittelu)","(en,Planning)","(et,Kavandamine)"}'::common.localized_text[]),
 ('{"(fi,Toteutus)","(en,Implementation)","(et,Teostamine)"}'::common.localized_text[]),
 ('{"(fi,Kunnossapito)","(en,Maintenance)","(et,Hooldamine)"}'::common.localized_text[]);

CREATE TABLE projects.project_phase (
 id SERIAL PRIMARY KEY,
 name common.localized_text[],
 validity daterange
);

INSERT INTO projects.project_phase (name) VALUES
 ('{"(fi,Esiselvitys)","(en,Pre-study)"}'),
 ('{"(fi,Liikenneselvitys)","(en,Traffic study)"}'),
 ('{"(fi,Tarveselvitys)","(en,Feasibility study)"}'),
 ('{"(fi,Yleissuunnitelma + YVA)","(en,General plan + YVA)"}'),
 ('{"(fi,Yleissuunnitelma)","(en,General plan)"}'),
 ('{"(fi,Toimenpidesuunnitelma)","(en,Action plan)"}'),
 ('{"(fi,Aluevaraussuunnitelma)","(en,Area reserve plan)"}'),
 ('{"(fi,\"Vesiluvan hakeminen (vesilupasuunnittelu)\")","(en,\"Water permit request (water permit planning)\")"}'),
 ('{"(fi,Vesiväylän yleissuunnittelu)","(en,Waterway general planning)"}'),
 ('{"(fi,Tiesuunnitelma)","(en,Road plan)"}'),
 ('{"(fi,Täydennetty tiesuunnitelma)","(en,Fulfilled road plan)"}'),
 ('{"(fi,Ratasuunnitelma)","(en,Rail plan)"}'),
 ('{"(fi,Rakentamissuunnitelma)","(en,Construction plan)"}'),
 ('{"(fi,Rakennussuunnitelma)","(en,Building plan)"}'),
 ('{"(fi,Rakentaminen)","(en,Construction)"}'),
 ('{"(fi,Päällystys)","(en,Road pavement)"}'),
 ('{"(fi,Korjaussuunnitelma)","(en,Repair plan)"}'),
 ('{"(fi,Katusuunnitelma)","(en,Street plan)"}'),
 ('{"(fi,Parantamissuunnitelma)","(en,Implement plan)"}'),
 ('{"(fi,Muu)","(en,Other)"}'),
 ('{"(fi,Peruskorjaus)","(en,Basic repair)"}'),
 ('{"(fi,Perusparannus/rakenteenparantaminen)","(en,Basic or structural improvement)"}');


CREATE TABLE users.user (
  id SERIAL PRIMARY KEY,
  name TEXT,
  roles TEXT[]
);

CREATE TABLE projects.projectgroup (
    id SERIAL PRIMARY KEY,
    geometry geometry,
    name TEXT,
    description TEXT,
    county TEXT,
    phase INT REFERENCES projects.projectgroup_phase (id),
    url TEXT,
    created timestamp without time zone NOT NULL DEFAULT now(),
    deleted timestamp without time zone,
    modified timestamp without time zone,
    created_by INT REFERENCES users.user (id),
    modified_by INT REFERENCES users.user (id),
    deleted_by INT REFERENCES users.user (id)
);

CREATE TABLE projects.project (
    id SERIAL PRIMARY KEY,
    geometry geometry,
    name TEXT,
    description TEXT,
    state INT REFERENCES projects.project_state (id),
    phase INT REFERENCES projects.project_phase (id),
    url TEXT,
    duration daterange, -- start/end dates

    created timestamptz NOT NULL DEFAULT now(),
    deleted timestamptz,
    modified timestamptz,
    projectgroup_id INT REFERENCES projects.projectgroup (id),

    created_by INT REFERENCES users.user (id),
    modified_by INT REFERENCES users.user (id),
    deleted_by INT REFERENCES users.user (id)
);

CREATE TABLE projects.assignment (
    id SERIAL PRIMARY KEY,
    geometry geometry,
    name TEXT,
    description TEXT,
    url TEXT,
    project_id INT REFERENCES projects.project (id),

    created timestamptz NOT NULL DEFAULT now(),
    deleted timestamptz,
    modified timestamptz,
    projectgroup_id INT REFERENCES projects.projectgroup (id),

    created_by INT REFERENCES users.user (id),
    modified_by INT REFERENCES users.user (id),
    deleted_by INT REFERENCES users.user (id)

);


-- Insert test data here (TODO: these must be moved elsewhere after poc)
INSERT INTO projects.projectgroup (name, description, phase) VALUES
  ('Proof of concept planning projects','This is the 1st test project group for PoC planning projects', 1),
  ('Implementation projects','The 2nd test project group for implementation', 2);

INSERT INTO projects.project (name,description,state,phase,projectgroup_id,geometry) VALUES
 ('Plan AWS setup project','Project for planning the AWS setup', 2, 1, 1, st_setsrid(st_geomfromgeojson('{"type": "LineString", "coordinates": [[2696394.7767341174, 8243938.0978665855],[2946280.7142341174, 8257578.7228665855],[2682290.0892341174, 8102122.4728665855],[2961899.4642341174, 8085200.5978665855]]}'), 3857)),
 ('Implement PoC', 'Project for implementing the PoC', 3,2,2, st_setsrid(st_geomfromgeojson('{"type": "LineString", "coordinates": [[2753413.5267341174, 8274627.1603665855],[2975080.7142341174, 8044063.0978665855]]}'), 3857));


CREATE FUNCTION projects.mvt_projectgroup_projects(
   id INT, xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
  FROM (SELECT CONCAT(p.name) AS tooltip,
               p.id,
               ST_AsMVTGeom(p.geometry,
                            st_makebox2d(st_makepoint($2, ymin),
                                         st_makepoint($4, ymax)),
                            4096, NULL, false)
          FROM projects.project p
         WHERE p.projectgroup_id = $1) AS tile;
$$ LANGUAGE SQL STABLE;

CREATE TYPE common.search_result AS (
  type TEXT,
  id INT,
  label TEXT,
  data JSON
);

CREATE FUNCTION projects.quicksearch(q TEXT)
RETURNS SETOF common.search_result
AS $$
SELECT ROW('projectgroup', pg.id, pg.name, NULL)::common.search_result
  FROM projects.projectgroup pg
 WHERE pg.name ILIKE '%'||q||'%'
    OR pg.description ILIKE '%'||q||'%'
UNION ALL
SELECT ROW('project', p.id, p.name, NULL)::common.search_result
  FROM projects.project p
 WHERE p.name ILIKE '%'||q||'%'
    OR p.description ILIKE '%'||q||'%'
$$ LANGUAGE SQL STABLE;

CREATE TYPE common.authinfo AS (
  authenticated bool,
  username text,
  email text
);

CREATE FUNCTION projects.whoami() RETURNS common.authinfo
AS $$
BEGIN
 IF current_user = 'teet_anon' THEN
   RETURN ROW(false, NULL, NULL)::common.authinfo;
 ELSE
   RETURN ROW(true,
              current_setting('request.jwt.claim.cognito:username'),
              current_setting('request.jwt.claim.email'))::common.authinfo;
 END IF;
END;
$$ LANGUAGE plpgsql STABLE;

GRANT USAGE ON SCHEMA projects to teet_anon;
GRANT USAGE ON SCHEMA projects to teet_user;
GRANT USAGE ON SCHEMA common TO teet_anon;
GRANT USAGE ON SCHEMA common TO teet_user;

GRANT SELECT ON projects.project TO teet_user;
GRANT SELECT ON projects.projectgroup TO teet_user;

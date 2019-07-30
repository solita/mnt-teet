CREATE SCHEMA metadata;
CREATE SCHEMA projects;
CREATE SCHEMA common;
CREATE SCHEMA users;

CREATE EXTENSION postgis;

CREATE TYPE metadata.fairway_type AS ENUM ('road','rail','water');

CREATE TYPE common.localized_text AS (
  lang TEXT, -- language code
  text TEXT
);

CREATE TABLE metadata.project_state (
 id SERIAL,
 name common.localized_text[],
 validity daterange
);

INSERT INTO metadata.project_state (name) VALUES
 ('{"(fi,Ehdolla)","(en,Proposed)"}'::common.localized_text[]),
 ('{"(fi,Valmisteilla)","(en,In preparation)"}'::common.localized_text[]),
 ('{"(fi,Käynnissä)","(en,Started)"}'::common.localized_text[]),
 ('{"(fi,Keskeytetty)","(en,Halted)"}'::common.localized_text[]),
 ('{"(fi,Tarkastuksessa)","(en,In review)"}'::common.localized_text[]),
 ('{"(fi,Valmis)","(en,Finished)"}'::common.localized_text[]),
 ('{"(fi,Hyväksytty)","(en,Approved)"}'::common.localized_text[]),
 ('{"(fi,Muu)","(en,Other)"}'::common.localized_text[]);

CREATE TABLE metadata.projectgroup_phase (
 id SERIAL,
 name common.localized_text[],
 validity daterange
);

INSERT INTO metadata.projectgroup_phase (name) VALUES
 ('{"(fi,Suunnittelu)","(en,Planning)"}'::common.localized_text[]),
 ('{"(fi,Toteutus)","(en,Implementation)"}'::common.localized_text[]),
 ('{"(fi,Kunnossapito)","(en,Maintenance)"}'::common.localized_text[]);

CREATE TABLE metadata.project_phase (
 id SERIAL,
 name TEXT,
 validity daterange
);

INSERT INTO metadata.project_phase (name) VALUES
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
    geometry geometry(Geometry,4326),
    name TEXT,
    description TEXT,
    duration daterange, -- start/end dates
    county TEXT,
    phase metadata.projectgroup_phase,
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
    geometry geometry(Geometry,4326),
    name TEXT,
    description TEXT,
    state metadata.project_state,
    phase metadata.project_phase,
    url TEXT,

    created timestamp without time zone NOT NULL DEFAULT now(),
    deleted timestamp without time zone,
    modified timestamp without time zone,
    projectgroup_id INT REFERENCES projects.projectgroup (id),

    created_by INT REFERENCES users.user (id),
    modified_by INT REFERENCES users.user (id),
    deleted_by INT REFERENCES users.user (id)
);

CREATE TABLE projects.assignment (
    id SERIAL PRIMARY KEY,
    geometry geometry(Geometry,4326),
    name TEXT,
    description TEXT,
    url TEXT,
    project_id INT REFERENCES projects.project (id),

    created timestamp without time zone NOT NULL DEFAULT now(),
    deleted timestamp without time zone,
    modified timestamp without time zone,
    projectgroup_id INT REFERENCES projects.projectgroup (id),

    created_by INT REFERENCES users.user (id),
    modified_by INT REFERENCES users.user (id),
    deleted_by INT REFERENCES users.user (id)

);

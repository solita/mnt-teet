-- Create tables if the don't exist

CREATE TABLE IF NOT EXISTS cadastre.katastriyksus (
    id SERIAL PRIMARY KEY,
    tunnus character varying(15),
    haldusyksuse_kood character varying(4),
    maakonna_nimi character varying(100),
    omavalitsuse_nimi character varying(100),
    asustusyksuse_nimi character varying(100),
    lahiaadress character varying(240),
    registreeritud date,
    muudetud date,
    sihtotstarve_1 character varying(50),
    sihtotstarve_2 character varying(50),
    sihtotstarve_3 character varying(50),
    so_protsent_1 double precision,
    so_protsent_2 double precision,
    so_protsent_3 double precision,
    pindala double precision,
    ruumikuju_pindala real,
    registreeritud_yhik character varying(5),
    haritav_maa double precision,
    looduslik_rohumaa double precision,
    metsamaa double precision,
    ouemaa double precision,
    muu_maa double precision,
    kinnistu_nr character varying(50),
    moodustatud date,
    moodistaja character varying(150),
    moodustamisviis character varying(150),
    registreerimisviis character varying(150),
    omandivorm character varying(50),
    maksustamishind double precision,
    marketekst character varying(100),
    eksport date,
    geom geometry(GeometryZM,3301)
);

CREATE INDEX IF NOT EXISTS katastriyksus_geom_geom_idx ON cadastre.katastriyksus USING GIST (geom gist_geometry_ops_2d);

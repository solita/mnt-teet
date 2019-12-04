CREATE OR REPLACE FUNCTION teet.thk_project_related_cadastral_units(entity_id BIGINT, distance INTEGER)
RETURNS JSON AS
$$
  SELECT json_agg(units.row)
    FROM (SELECT json_build_object(
                   'id', ky.id,
                   'tunnus', ky.tunnus,
                   'haldusyksuse_kood', ky.haldusyksuse_kood,
                   'maakonna_nimi', ky.maakonna_nimi,
                   'omavalitsuse_nimi', ky.omavalitsuse_nimi,
                   'asustusyksuse_nimi', ky.asustusyksuse_nimi,
                   'lahiaadress', ky.lahiaadress,
                   'registreeritud', ky.registreeritud,
                   'muudetud', ky.muudetud,
                   'sihtotstarve_1', ky.sihtotstarve_1,
                   'sihtotstarve_2', ky.sihtotstarve_2,
                   'sihtotstarve_3', ky.sihtotstarve_3,
                   'so_protsent_1', ky.so_protsent_1,
                   'so_protsent_2', ky.so_protsent_2,
                   'so_protsent_3', ky.so_protsent_3,
                   'pindala', ky.pindala,
                   'ruumikuju_pindala', ky.ruumikuju_pindala,
                   'registreeritud_yhik', ky.registreeritud_yhik,
                   'haritav_maa', ky.haritav_maa,
                   'looduslik_rohumaa', ky.looduslik_rohumaa,
                   'metsamaa', ky.metsamaa,
                   'ouemaa', ky.ouemaa,
                   'muu_maa', ky.muu_maa,
                   'kinnistu_nr', ky.kinnistu_nr,
                   'moodustatud', ky.moodustatud,
                   'moodistaja', ky.moodistaja,
                   'moodustamisviis', ky.moodustamisviis,
                   'registreerimisviis', ky.registreerimisviis,
                   'omandivorm', ky.omandivorm,
                   'maksustamishind', ky.maksustamishind,
                   'marketekst', ky.marketekst,
                   'eksport', ky.eksport) as row
            FROM cadastre.katastriyksus ky
            JOIN teet.entity e ON ST_DWithin(ky.geom, e.geometry, distance)
           WHERE e.id = entity_id) units;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.mvt_cadastral_units(xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
  FROM (SELECT ky.id,
               ky.tunnus AS tooltip,
               ST_AsMVTGeom(ky.geom,
                            ST_SetSRID(ST_MakeBox2D(ST_MakePoint($1,$2), ST_MakePoint($3,$4)), 3301),
                            4096, NULL, false)
          FROM cadastre.katastriyksus ky
         WHERE ST_DWithin(ky.geom,
                          ST_Setsrid(ST_MakeBox2D(ST_MakePoint($1,$2), ST_MakePoint($3,$4)), 3301),
                          1000)) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.geojson_thk_project_related_cadastral_units(entity_id BIGINT, distance INTEGER) RETURNS TEXT
AS $$
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(ky.geom)::json as geometry,
                       json_build_object('id', ky.id, 'tooltip', json_build_object('tunnus', ky.tunnus, 'haldusyksuse_kood', ky.haldusyksuse_kood, 'maakonna_nimi', ky.maakonna_nimi, 'omavalitsuse_nimi', ky.omavalitsuse_nimi, 'lahiaadress', ky.lahiaadress, 'registreeritud', ky.registreeritud, 'muudetud', ky.muudetud, 'sihtotstarve_1', ky.sihtotstarve_1, 'sihtotstarve_2', ky.sihtotstarve_2, 'sihtotstarve_3', ky.sihtotstarve_3, 'moodustatud', ky.moodustatud , 'moodistaja', ky.moodistaja, 'moodustamisviis', ky.moodustamisviis, 'registreerimisviis', ky.registreerimisviis, 'omandivorm', ky.omandivorm)) as properties
                  FROM cadastre.katastriyksus ky
                  JOIN teet.entity e ON ST_DWithin(ky.geom::geometry, e.geometry, distance::double precision)
                 WHERE e.id = entity_id) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

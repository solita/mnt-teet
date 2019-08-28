-- Functions to fetch information from road registry

CREATE OR REPLACE FUNCTION teet.road_part_geometry (_tee INTEGER, _carriageway INTEGER, _km NUMRANGE)
RETURNS GEOMETRY
AS $$
DECLARE
  geom geometry;
  r RECORD;
  startm INTEGER;
  endm INTEGER;
  mrange NUMRANGE;
BEGIN
  -- Convert km_range to start/end meters
  startm := (lower(_km)*1000.0)::INTEGER;
  endm := (upper(_km)*1000.0)::INTEGER;
  mrange := numrange(startm::NUMERIC, endm::NUMERIC);
  FOR r IN SELECT g.stee, g.algus, g.lopp, g.teeosa, g.shap_leng, St_LineMerge(g.mgeom) AS line
             FROM gis.riigi_teeosa_mgeom g
            WHERE g.tee = _tee and g.stee = _carriageway
              AND mrange && numrange(g.algus::NUMERIC, g.lopp::NUMERIC)
            ORDER BY g.teeosa
  LOOP
    IF startm > r.algus AND endm < r.lopp THEN
      -- Road is fully within this one segment
      RAISE NOTICE 'wanted part % - % is fully in segment start/end: % - %', startm, endm, r.algus, r.lopp;
      geom := ST_LineSubstring(r.line, LEAST(startm/r.shap_leng,1), LEAST(endm/r.shap_leng,1));
    ELSIF startm > r.algus THEN
      -- Road starts in this segment but continues in the next
      RAISE NOTICE '% - % starts in this segment: % - %', startm, endm, r.algus, r.lopp;
      geom := ST_LineSubstring(r.line, LEAST(startm/r.shap_leng,1), 1.0);
    ELSIF endm < r.lopp THEN
      -- Road ends in this segment
      RAISE NOTICE '% - % ends in this segment: % - % => %', startm, endm, r.algus, r.lopp, endm/r.lopp;
      geom := ST_LineMerge(ST_Collect(geom, ST_LineSubstring(r.line, 0.0, LEAST(endm/r.shap_leng,1))));
    ELSE
      -- This segment is fully in the wanted road part
      RAISE NOTICE 'wanted road % - % contains segment fully % - %', startm, endm, r.algus, r.lopp;
      geom := ST_LineMerge(ST_Collect(geom, r.line));
    END IF;
  END LOOP;
  RETURN geom;
END
$$ LANGUAGE plpgsql STABLE;

-- Trigger to update thk_project geometry
CREATE OR REPLACE FUNCTION impl.update_project_geometry() RETURNS TRIGGER
AS $$
BEGIN
  NEW.geometry := teet.road_part_geometry(NEW.road_nr::INTEGER, NEW.carriageway::INTEGER, NEW.km_range);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_project_geometry ON teet.thk_project;
CREATE TRIGGER update_project_geometry
BEFORE INSERT OR UPDATE
ON teet.thk_project
FOR EACH ROW EXECUTE PROCEDURE impl.update_project_geometry();


-- MVT layer to get project geometries
CREATE OR REPLACE FUNCTION teet.mvt_thk_projects(
   q TEXT, xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
  FROM (SELECT CONCAT(p.name) AS tooltip,
               p.id,
               ST_AsMVTGeom(p.geometry,
                            ST_SetSRID(ST_MakeBox2D(ST_MakePoint($2, ymin),
                                                    ST_MakePoint($4, ymax)), 3301),
                            4096, NULL, false)
          FROM teet.thk_project_search p
         WHERE ST_DWithin(p.geometry,
                          ST_Setsrid(ST_MakeBox2D(ST_MakePoint($2, ymin),
                                                  ST_MakePoint($4, ymax)), 3301),
                          1000)
           AND (q IS NULL OR q = '' OR p.searchable_text LIKE '%'||q||'%')) AS tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

-- GeoJSON layer of all project centroids (to show pins at high zoom level)
CREATE OR REPLACE FUNCTION teet.geojson_thk_project_pins(q TEXT) RETURNS TEXT
AS $$
WITH projects AS (
SELECT *
  FROM teet.thk_project_search p
 WHERE (q IS NULL OR q = '' OR p.searchable_text ILIKE '%'||q||'%')
)
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
                array_to_json(array_agg(f)) as features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(ST_Centroid(p.geometry))::json as geometry,
                       json_object(ARRAY['id', p.id, 'tooltip', p.name]::TEXT[]) AS properties
                  FROM projects p) f) fc;
$$ LANGUAGE SQL STABLE;


-- GeoJSON layer for a single project
CREATE OR REPLACE FUNCTION teet.geojson_thk_project(id TEXT) RETURNS TEXT
AS $$
WITH projects AS (
    SELECT *
    FROM teet.thk_project_search p
    WHERE p.id = $1
)
SELECT row_to_json(fc)::TEXT
FROM (SELECT 'FeatureCollection' as type,
             array_to_json(array_agg(f)) as features
      FROM (SELECT 'Feature' as type,
                   ST_AsGeoJSON(p.geometry)::json as geometry,
                   json_object(ARRAY['id', p.id, 'tooltip', p.name]::TEXT[]) AS properties
            FROM projects p) f) fc;
$$ LANGUAGE SQL STABLE;

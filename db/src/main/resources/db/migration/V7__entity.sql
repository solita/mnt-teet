-- Entity types, add new ones as need
CREATE TYPE entity_type AS ENUM ('project', 'activity', 'task', 'document');

CREATE TABLE teet.entity (
  id BIGINT PRIMARY KEY, -- :db/id of the entity
  type entity_type,
  tooltip TEXT, -- Tooltip to show on map
  geometry geometry(Geometry,3301) -- Geometry of this entity
);

CREATE INDEX entity_type_idx ON teet.entity (type);
CREATE INDEX entity_geometry_idx ON teet.entity USING GIST (geometry);

CREATE OR REPLACE FUNCTION teet.road_part_geometry (_tee INTEGER, _carriageway INTEGER, _km NUMRANGE)
RETURNS GEOMETRY
AS $$
DECLARE
  geom geometry;
  r RECORD;
  startm INTEGER;
  endm INTEGER;
  mrange NUMRANGE;
  start_point NUMERIC;
  end_point NUMERIC;
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
    -- Clamp start/end point for this segment between 0 and 1
    start_point := GREATEST(0,LEAST((startm-r.algus)/r.shap_leng,1));
    end_point := GREATEST(0,LEAST((endm-r.algus)/r.shap_leng,1));
    --RAISE NOTICE 'startm - endmm: % - % , algus: %, len: %, taking part % - %', startm, endm, r.algus, r.shap_leng, start_point, end_point;
    geom := ST_LineMerge(ST_Collect(geom, ST_LineSubstring(r.line, start_point, end_point)));
  END LOOP;
  RETURN geom;
END
$$ LANGUAGE plpgsql STABLE;

-- Create function that allows backend to upsert entity info
CREATE FUNCTION teet.store_entity_info(id TEXT, type entity_type, tooltip TEXT,
                                       road INTEGER, carriageway INTEGER, start_m INTEGER, end_m INTEGER)
RETURNS BIGINT
AS $$
INSERT
  INTO teet.entity
       (id, type, tooltip, geometry)
VALUES (id::BIGINT, type, tooltip,
        teet.road_part_geometry(road, carriageway, numrange(start_m/1000,end_m/1000)))
ON CONFLICT (id) DO
UPDATE SET tooltip = EXCLUDED.tooltip,
           geometry = teet.road_part_geometry(road, carriageway, numrange(start_m/1000,end_m/1000))
RETURNING id;
$$ LANGUAGE SQL SECURITY DEFINER;

CREATE ROLE teet_backend NOLOGIN;
GRANT teet_backend TO authenticator;
GRANT USAGE ON SCHEMA teet TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.store_entity_info TO teet_backend;


-- Create functions for fetching entity MVT and GeoJSON
CREATE OR REPLACE FUNCTION teet.mvt_entities(ids BIGINT[],
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
  FROM (SELECT e.tooltip,
               e.id, e.type,
               ST_AsMVTGeom(e.geometry,
                            ST_SetSRID(ST_MakeBox2D(ST_MakePoint($2, ymin),
                                                    ST_MakePoint($4, ymax)), 3301),
                            4096, NULL, false)
          FROM teet.entity e
         WHERE e.id = ANY(ids)) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

CREATE FUNCTION teet.geojson_entities(ids BIGINT[])
RETURNS TEXT
AS $$
SELECT row_to_json(fc)::TEXT
  FROM (SELECT 'FeatureCollection' as type,
               array_to_json(array_agg(f)) AS features
          FROM (SELECT 'Feature' as type,
                       ST_AsGeoJSON(e.geometry)::json AS geometry,
                       json_build_object('id', e.id::text,
                                         'tooltip', e.tooltip,
                                         'type', e.type) AS properties
                  FROM teet.entity e
                 WHERE e.id = ANY(ids)) f) fc;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION teet.mvt_entities TO teet_user;
GRANT EXECUTE ON FUNCTION teet.mvt_entities TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.geojson_entities TO teet_user;
GRANT EXECUTE ON FUNCTION teet.geojson_entities TO teet_backend;

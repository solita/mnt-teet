-- Functions to fetch datasource features as map layers

CREATE OR REPLACE FUNCTION teet.mvt_features(datasource INT, types TEXT[],
                                             xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
    RETURNS bytea
AS $$
SELECT ST_AsMVT(tile) AS mvt
FROM (SELECT f.label AS tooltip,
             f.id, f.type,
             datasource AS datasource,
             ST_AsMVTGeom(f.geometry,
                          ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                                  ST_MakePoint($5, ymax)), 3301),
                          4096, NULL, false)
       FROM teet.feature f
      WHERE ST_DWithin(f.geometry,
                       ST_SetSRID(ST_MakeBox2D(ST_MakePoint($3, ymin),
                                               ST_MakePoint($5, ymax)), 3301),
                       1000)
        AND f.type = ANY(types)) tile;
$$ LANGUAGE SQL STABLE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION teet.mvt_features(INT,TEXT[],NUMERIC,NUMERIC,NUMERIC,NUMERIC) TO teet_user;

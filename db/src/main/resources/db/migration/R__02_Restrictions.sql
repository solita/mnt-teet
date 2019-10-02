-- Restrictions

CREATE OR REPLACE FUNCTION restrictions.restrictions_tables() RETURNS SETOF TEXT AS $$
SELECT tablename::text
  FROM pg_catalog.pg_tables
 WHERE schemaname = 'restrictions'
   AND tablename LIKE 'kitsendus_%'
   AND tablename != 'kitsendus_';
$$ LANGUAGE SQL STABLE;

CREATE OR REPLACE FUNCTION teet.restriction_map_selections() RETURNS JSON AS $$
DECLARE
  selections JSON[];
  r RECORD;
  z RECORD;
  tbl TEXT;
  dynsql TEXT;
  zones TEXT[];
BEGIN
  FOR r IN SELECT * FROM restrictions.restrictions_tables()
  LOOP
    RAISE NOTICE 'table %', r.restrictions_tables;
    tbl := r.restrictions_tables;
    -- Fast way to get distinct values of indexed column (get all voond values)
    dynsql := 'WITH RECURSIVE t(n) AS (' ||
              '  SELECT MIN(voond) FROM restrictions.'||tbl||
              ' UNION '
              ' SELECT (SELECT voond FROM restrictions.'||tbl||' WHERE voond > n ORDER BY voond LIMIT 1) '
              ' FROM t WHERE n IS NOT NULL '
              ') SELECT n FROM t ORDER BY n;';
    zones := '{}'::TEXT[];
    FOR z IN EXECUTE dynsql
    LOOP
      zones := zones || z.n;
    END LOOP;
    selections := selections ||
                  json_build_object('type', SUBSTRING(tbl, 11),
                                    'layers', array_to_json(zones));
  END LOOP;
  RETURN to_json(selections);
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;

CREATE OR REPLACE FUNCTION teet.mvt_restrictions(
   type TEXT, layers TEXT, xmin NUMERIC, ymin NUMERIC, xmax NUMERIC, ymax NUMERIC)
RETURNS bytea
AS $$
DECLARE
  t RECORD;
  r RECORD;
  voond_values TEXT;
  tbl TEXT;
  dynsql TEXT;
  mvt BYTEA;
BEGIN
  -- Layers as an array (formatted as SQL value to avoid injection)
  voond_values = format('%L', regexp_split_to_array(layers, ','));

  tbl := 'kitsendus_' || type;
  FOR t IN SELECT * FROM restrictions.restrictions_tables()
  LOOP
    IF t.restrictions_tables != tbl THEN
      CONTINUE;
    END IF;
    dynsql := 'SELECT ST_AsMVT(tile) AS mvt ' ||
              ' FROM (SELECT CONCAT(p.voondi_nimi) AS tooltip, ' ||
              '              '''||type||''' as rt,' ||
              '              p.id, ' ||
              '              ST_AsMVTGeom(p.geom, ' ||
              '                           ST_SetSRID(ST_MakeBox2D(ST_MakePoint('||xmin||', '||ymin||'), ' ||
              '                                      ST_MakePoint('||xmax||', '||ymax||')), 3301), ' ||
              '                           4096, NULL, false) ' ||
              '         FROM restrictions.' || tbl || ' p ' ||
              '        WHERE ST_DWithin(p.geom, ' ||
              '                         ST_Setsrid(ST_MakeBox2D(ST_MakePoint('||xmin||', '||ymin||'), ' ||
              '                                    ST_MakePoint('||xmax||', '||ymax||')), 3301), ' ||
              '                         1000)'
              '          AND '||voond_values||' @> ARRAY[p.voond]) AS tile';
     RAISE NOTICE 'dynsql: %', dynsql;
    FOR r in EXECUTE dynsql
    LOOP
      RAISE NOTICE 'got result: %', r;
      RETURN r.mvt;
    END LOOP;
  END LOOP;
  RAISE EXCEPTION 'No restriction type %', type;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER;


CREATE OR REPLACE FUNCTION ensure_restrictions_indexes() RETURNS VOID AS $$
DECLARE
  r RECORD;
  tbl TEXT;
  dynsql TEXT;
BEGIN
  FOR r IN SELECT * FROM restrictions.restrictions_tables()
  LOOP
    tbl := r.restrictions_tables;
    dynsql := 'CREATE INDEX IF NOT EXISTS ' || tbl || '_voond_idx ON restrictions.' || tbl || '  (voond)';
    EXECUTE dynsql;
  END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Call fn to create missing indexes
select * from ensure_restrictions_indexes();

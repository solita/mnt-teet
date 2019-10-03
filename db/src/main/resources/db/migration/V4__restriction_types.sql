CREATE TYPE teet.restriction_list_item AS (
  type text, -- table from which this restriction is
  id integer,
  vid text,
  kpo_vid text,
  voond text,
  voondi_nimi text,
  toiming text,
  muudetud date,
  seadus text
);

-- Type for fetching restriction items for map
CREATE TYPE restrictions.restriction_mvt_item AS (
  type text, -- table from which this restriction is
  id integer,
  tooltip text,
  geom geometry
);

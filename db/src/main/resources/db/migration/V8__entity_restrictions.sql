-- Link restriction ids to entity id

CREATE TABLE teet.entity_restrictions (
  entity_id BIGINT PRIMARY KEY REFERENCES teet.entity (id),
  restrictions TEXT[] -- array of kpo_vid restriction ids
);

CREATE TYPE teet.restriction_list_item_geom AS (
  type text, -- table from which this restriction is
  id integer,
  vid text,
  kpo_vid text,
  voond text,
  voondi_nimi text,
  toiming text,
  muudetud date,
  seadus text,
  geometry geometry
);

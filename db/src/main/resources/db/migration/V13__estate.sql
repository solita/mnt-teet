-- Save property registry estate responses
CREATE TABLE teet.estate (
  id TEXT PRIMARY KEY, -- Estate id
  fetched_at TIMESTAMPTZ DEFAULT NOW(), -- When this was fetched from X-road property register
  payload JSONB -- JSONB of the data parsed from the SOAP response
);


GRANT ALL PRIVILEGES ON teet.estate TO teet_backend;

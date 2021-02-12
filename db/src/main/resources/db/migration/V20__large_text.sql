CREATE TABLE teet.large_text (hash bytea PRIMARY KEY, text TEXT);

-- Function to upsert new text, returns hash
CREATE FUNCTION teet.store_large_text(text TEXT) RETURNS TEXT
AS $$
INSERT INTO teet.large_text (hash,text)
VALUES (sha256(convert_to($1::TEXT,'UTF-8')), $1)
ON CONFLICT (hash) DO UPDATE SET hash=large_text.hash
RETURNING encode(sha256(convert_to($1::TEXT,'UTF-8')),'hex');
$$ LANGUAGE SQL;

-- Function to fetch text by hash
CREATE FUNCTION teet.fetch_large_text(hash TEXT) RETURNS TEXT
AS $$
SELECT lt.text FROM teet.large_text lt WHERE lt.hash=decode($1, 'hex')
$$ LANGUAGE SQL IMMUTABLE;

GRANT ALL ON TABLE teet.large_text TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.store_large_text(TEXT) TO teet_backend;
GRANT EXECUTE ON FUNCTION teet.fetch_large_text(TEXT) TO teet_backend;

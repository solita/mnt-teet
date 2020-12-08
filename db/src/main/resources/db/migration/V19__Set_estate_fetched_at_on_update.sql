-- Estate data is upserted, hence without the trigger, fetched_at is set only on create,
-- but not on subsequent updates. Add trigger to set fetched_at when updating as well.
CREATE OR REPLACE FUNCTION teet.update_estate_fetch_timestamp()
RETURNS TRIGGER AS $$
BEGIN
new.fetched_at = NOW();
return new;
END;
$$ LANGUAGE 'plpgsql';

CREATE TRIGGER update_estate_fetch_timestamp BEFORE UPDATE
ON teet.estate
FOR EACH ROW
EXECUTE PROCEDURE teet.update_estate_fetch_timestamp();

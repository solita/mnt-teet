-- The format used for caching the estate data from property registry
-- changed, and we need to empty the cached responses stored in the
-- old form.
TRUNCATE TABLE teet.estate;

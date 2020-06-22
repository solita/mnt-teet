CREATE OR REPLACE FUNCTION teet.audit_event("user" UUID, event TEXT, args JSON)
RETURNS VOID
AS $$
INSERT INTO teet.auditlog ("user", event, args) VALUES ($1, $2, $3::JSONB);
$$ LANGUAGE SQL;

GRANT EXECUTE ON FUNCTION teet.audit_event(UUID,TEXT,JSON) TO teet_backend;

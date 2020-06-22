-- Log for audit events

CREATE TABLE teet.auditlog (
  id BIGSERIAL,
  timestamp TIMESTAMPTZ DEFAULT NOW(),
  "user" UUID,
  event TEXT,
  args JSONB
);

#!/bin/sh

echo "Starting PostgREST for project registry local dev db"

docker run --rm -p 3000:3000 \
  -e PGRST_DB_URI="postgres://teis@host.docker.internal:5432/teis" \
  -e PGRST_DB_ANON_ROLE="teis" \
  -e PGRST_DB_SCHEMA="projects" \
  postgrest/postgrest

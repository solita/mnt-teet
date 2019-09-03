#!/usr/bin/env bash
set -eu

JWKS_URL="https://cognito-idp.$COGNITO_REGION.amazonaws.com/$COGNITO_POOL_ID/.well-known/jwks.json"
echo Downloading JWKS from $JWKS_URL

curl -o /tmp/postgrest-key.jwks $JWKS_URL

echo STARTING PostgREST
postgrest /etc/postgrest.conf

#!/bin/sh

DBUSER="authenticator"
ANON="teet_anon"

#DBUSER="teet"
#ANON="teet"

ARGS=""
URI=""

if [[ "$OSTYPE" == "darwin"* ]]; then
    ARGS="--rm -p 3000:3000"
    URI="postgres://$DBUSER@host.docker.internal:5432/teet"
elif [[ "$OSTYPE" == "linux-gnu" ]]; then
    ARGS="-d --rm --net=host -p 3000:3000"
    URI="postgres://$DBUSER@127.0.0.1:5432/teet"
else
    echo "OS not supported"
    exit
fi

docker run $ARGS \
       -e PGRST_DB_URI="$URI" \
       -e PGRST_DB_ANON_ROLE="$ANON" \
       -e PGRST_DB_SCHEMA="teet" \
       -e PGRST_JWT_SECRET="secret1234567890secret1234567890" \
       postgrest/postgrest:v6.0.1-53b606e

       #-e PGRST_ROLE_CLAIM_KEY=".\"custom:role\"" \
       #-e COGNITO_REGION="eu-central-1" \
       #-e COGNITO_POOL_ID="eu-central-1_esU4rtEAi" \
       #mnt-teet/teet-api

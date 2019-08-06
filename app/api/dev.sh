#!/bin/sh

DBUSER="authenticator"
ANON="teis_anon"

#DBUSER="teis"
#ANON="teis"

ARGS=""
URI=""

if [[ "$OSTYPE" == "darwin"* ]]; then
    ARGS="--rm -p 3000:3000"
    URI="postgres://$DBUSER@host.docker.internal:5432/teis"
elif [[ "$OSTYPE" == "linux-gnu" ]]; then
    ARGS="-d --rm --net=host -p 3000:3000"
    URI="postgres://$DBUSER@127.0.0.1:5432/teis"
else
    echo "OS not supported"
    exit
fi

#  -e PGRST_JWT_SECRET="{\"keys\":[{\"alg\":\"RS256\",\"e\":\"AQAB\",\"kid\":\"OF2giMEalKbFs3irp7iU7fHupT29ZbmJSTwjtR6mHCs=\",\"kty\":\"RSA\",\"n\":\"tFdsdaMYTMFapxRFtg92Ke6Jj80TYwp1PjnsNGbkRBN0rH5WYNgJEMadFuYU_dh5FImWIyqkjtrZ4G6qNIiAuJW27ATuSkcBfWVcWlinuALzf9iup1ZlbD8Y6uOLf3Z2da51qZ6NS2C7if0muV6fSRbgOqXSv9tE-nxmhRrcI8nljyNAnOixn9ovZtzgKMgOzD0vrbhCjHN6p_F6wSVydzPpxKj5ea8gMO0cOyybo6DZiUblcgUyt0GqKowhi8hEg6Ptkn1QZapzEM4HlLjJP5KNORNQhhREBd2MkEIkO9APtTcE8No6gwT2MiZagOPrdcjvixMqw-W3iP4VYp9llw\",\"use\":\"sig\"},{\"alg\":\"RS256\",\"e\":\"AQAB\",\"kid\":\"15hJ0DG/js99EDPq6I4vLWKqeER4h93Wz5Qr3/adc+0=\",\"kty\":\"RSA\",\"n\":\"qXSfU4ydbXrKlMK4SxT2f1hmmBjdAOEHQOn03qObCbYJjW_WwXQxNc2t7i3IiaQcT1okRUbCwpuvEnLr2mUD2OtOTa8EpllAVrIPQ1dQZg_OMcU8pQSZWWCWOO6gsCVWVDP_B_nl5AsaP4FxzxIFs6bbiG06BY8_UH6Tn7eAEdxVHL1WNCh4tE2hy4w2nEw852z0VbXpp8hsY7ugCLr7leDY1eAP9xutnqTB3RmJg7gU2Ws7wBurdhWCWCXO70LJeaKUaV0ZBbA_AcUJJwdilmFeQWkXxGZp8WevpQSnR3YDPQswGDULBEtlvvWkGQe93GfMSuVOIVW4ZBqBXQDjKw\",\"use\":\"sig\"}]}" \

docker run $ARGS \
       -e PGRST_DB_URI="$URI" \
       -e PGRST_DB_ANON_ROLE="$ANON" \
       -e PGRST_DB_SCHEMA="projects" \
       -e PGRST_ROLE_CLAIM_KEY=".\"custom:role\"" \
       -e COGNITO_REGION="eu-central-1" \
       -e COGNITO_POOL_ID="eu-central-1_esU4rtEAi" \
       teis-poc/teis-project-registry

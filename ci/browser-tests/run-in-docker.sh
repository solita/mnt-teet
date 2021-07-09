#!/bin/bash
set -exo pipefail
if [ -z "$CYPRESS_SITE_PASSWORD" ]; then
    echo "please enter site password (will be echoed)"
    read CYPRESS_SITE_PASSWORD
    echo thank you
    export CYPRESS_SITE_PASSWORD
fi

if [ -z "$CF" ]; then
    CF=cypress-localdev.json
fi
export CF
echo using cypress config file $CF
mkdir -p cypress-cache
docker run --entrypoint=/bin/bash --rm --net=host --shm-size=512M \
       -it -e CYPRESS_SITE_PASSWORD \
       -v $PWD:/e2e -v $PWD/cypress-cache:/root/.cache -w /e2e cypress/included:7.6.0 \
       -c "npm install && ./node_modules/cypress/bin/cypress run --config-file $CF $*"

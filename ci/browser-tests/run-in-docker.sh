#!/bin/sh
if [ -z "$CYPRESS_SITE_PASSWORD" ]; then
    echo "please enter site password (will be echoed)"
    read CYPRESS_SITE_PASSWORD
    echo thank you
    export CYPRESS_SITE_PASSWORD
fi
docker run --shm-size 512M -it -e CYPRESS_SITE_PASSWORD -v $PWD:/e2e -w /e2e cypress/included:5.6.0 

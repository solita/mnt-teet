#!/bin/bash
echo "Deploying Datomic Ions"

GROUP="teet-dev-datomic-Compute-13NV9SDSNGAR3"
REGION="eu-central-1"

cd ../../app/backend

clojure -Adev -m datomic.ion.dev \
        '{:op :deploy :group $GROUP :rev "$COMMIT" :region "$REGION"}'

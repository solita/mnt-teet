#!/bin/bash
echo "Deploying Datomic Ions"

GROUP=`aws ssm get-parameters --names "/teet/datomic/group" --query "Parameters[0].Value" | tr -d '"'`
REGION="eu-central-1"

cd ../../app/backend

clojure -Adev -m datomic.ion.dev \
        "{:op :deploy :group $GROUP :rev \"$COMMIT\" :region \"$REGION\"}"

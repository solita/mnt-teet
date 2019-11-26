#!/bin/bash
echo "Deploying Datomic Ions"

GROUP=`aws ssm get-parameters --names "/teet/datomic/group"  --query 'Parameters[*].Value' --output text`
REGION=`aws ssm get-parameters --names "/teet/datomic/region"  --query 'Parameters[*].Value' --output text`

cd ../../app/backend

clojure -Adev -m datomic.ion.dev \
        "{:op :deploy :group $GROUP :rev \"$COMMIT\" :region \"$REGION\"}"

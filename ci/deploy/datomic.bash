#!/bin/bash
echo "Deploying Datomic Ions"

GROUP=`aws ssm get-parameters --names "/teet/datomic/group" --query "Parameters[0].Value" | tr -d '"'`
REGION="eu-central-1"

cd ../../app/backend

clojure -Aion -m datomic.ion.dev \
        "{:op :deploy :group $GROUP :uname \"$CODEBUILD_RESOLVED_SOURCE_VERSION\" :region \"$REGION\"}"

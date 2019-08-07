#!/bin/sh

cd /opt/teet-db

mvn -DdatabaseUrl=$DB_URI -DdatabaseUser=$DB_USER -DdatabasePassword=$DB_PASSWORD flyway:migrate

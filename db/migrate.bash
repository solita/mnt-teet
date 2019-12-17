#!/bin/bash

echo Running Flyway migrations

export DB_URI=`aws ssm get-parameters --names "/teet/migrate/db-uri" --query "Parameters[0].Value" | tr -d '"'`
export DB_USER=`aws ssm get-parameters --names "/teet/migrate/db-user" --query "Parameters[0].Value" | tr -d '"'`
export DB_PASSWORD=`aws ssm get-parameters --names "/teet/migrate/db-password" --query "Parameters[0].Value" | tr -d '"'`

clojure -m migrate

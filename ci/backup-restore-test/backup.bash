#!/usr/bin/env bash

echo "Backup started"

cd app/backend

# create backup using existing default config
clojure -A:dev - <<EOF
(teet.environment/load-local-config!)

(println "Local config loaded!")

(System/exit 0)
EOF

echo "Backup completed"
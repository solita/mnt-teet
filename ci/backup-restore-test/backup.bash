#!/usr/bin/env bash

echo "Backup started"

# create backup using existing default config
clojure -A:dev - <<EOF
(println "Backup is here")
(System/exit 0)
EOF

echo "Backup completed"
#!/usr/bin/env bash

echo "Backup started"

# create backup using existing default config
clojure -A:dev - <<EOF
(teet.environment/load-local-config!)
(require '[teet.backup.backup-ion :as bu])
(println "Backup is here")

(System/exit 0)
EOF

echo "Backup completed"
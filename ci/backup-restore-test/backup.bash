#!/usr/bin/env bash

echo "Backup started"

# create backup using existing default config
clojure -A:dev - <<EOF
(teet.environment/load-local-config!)
(require '[teet.backup.backup-ion :as bu])
(bu/backup* {:input "{\"bucket\":\"teet-dev-documents\",\"file-key\":\"teet-dev-backup-Fri Apr 16 13:00:00 EEST 2021.edn.zip\"}"})

(System/exit 0)
EOF

echo "Backup completed"
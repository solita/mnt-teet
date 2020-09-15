#!/usr/bin/env bash

set -euo pipefail

DEST_BUCKET=teet-restoretest-`echo $USER|head -c 2`
SOURCE_BUCKET=teet-dev-documents


# uncomment these when running this for the first time / with a fresh dest bucket after manually deleting it:

echo "create bucket $DEST_BUCKET & clone $SOURCE_BUCKET to $DEST_BUCKET ? (y/n)"
read answer
echo "answer is $answer"
if [ x$answer = xy ]; then
  aws s3api create-bucket --bucket $DEST_BUCKET --create-bucket-configuration LocationConstraint=eu-central-1
  aws s3 sync s3://$SOURCE_BUCKET s3://$DEST_BUCKET
else
    echo "assuming test bucket $DEST_BUCKET aready exists"
fi


# 1. create backup using existing default config

clojure -A:dev - <<EOF
(teet.environment/load-local-config!)
(require '[teet.backup.backup-ion :as bu])
(bu/backup* {:input "{\"bucket\":\"teet-dev-documents\",\"file-key\":\"teet-dev-backup-Tue Sep 08 14:46:17 EEST 2020.edn.zip\"}"})

(System/exit 0)
EOF


# 2. restore  backup using modified config pointing to different database name and different document-storage buacket name
   
sed -e 's/:document-storage.*/:document-storage {:bucket-name "'$DEST_BUCKET'"}/' -e 's/:db-name ".*"/:db-name "'$DEST_BUCKET'"/' < $PWD/../../../mnt-teet-private/config.edn > restoretest-teetconfig.edn

clojure -A:dev - <<EOF
(teet.environment/load-local-config! "restoretest-teetconfig.edn")
(require '[teet.backup.backup-ion :as bu])
(bu/restore* {:input "{\"bucket\":\"teet-dev-documents\",\"file-key\":\"teet-dev-backup-2020-09-09.edn.zipp\"}"})
(System/exit 0)
EOF

;; todo: to use in ci, nede to detect possible errors & propagate the error exit from this script

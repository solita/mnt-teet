#!/usr/bin/env bash
set -eu

TEET_DIR_BUILD=`aws ssm get-parameters --names "/teet/s3/builddir"  --query 'Parameters[*].Value' --output text`
TEET_DIR_PUBLIC=`aws ssm get-parameters --names "/teet/s3/publicdir"  --query 'Parameters[*].Value' --output text`

echo "Fetching frontend build: $COMMIT"
aws s3 cp s3://$TEET_DIR_BUILD/teet-frontend/frontend-$COMMIT.zip frontend.zip
mkdir frontend
cd frontend
unzip ../frontend.zip

# Rename main.js to main-<commit>.js and modify index.html
mv js/main.js js/main-$COMMIT.js
sed -i -e "s/main.js/main-$COMMIT.js/g" index.html

cd ..
aws s3 rm s3://$TEET_DIR_PUBLIC --recursive
aws s3 sync frontend s3://$TEET_DIR_PUBLIC --acl public-read

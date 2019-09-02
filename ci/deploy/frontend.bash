#!/bin/bash
set -e

echo "Fetching frontend build: $COMMIT"
aws s3 cp s3://teet-build/teet-frontend/frontend-$COMMIT.zip frontend.zip
mkdir frontend
cd frontend
unzip ../frontend.zip

# Rename main.js to main-<commit>.js and modify index.html
mv js/main.js js/main-$COMMIT.js
sed -i -e "s/main.js/main-$COMMIT.js/g" index.html

cd ..
aws s3 rm s3://teet-dev-public --recursive
aws s3 sync frontend s3://teet-dev-public --acl public-read

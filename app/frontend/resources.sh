#!/usr/bin/env bash
set -eu

echo "Fetch resources"

mkdir resources/public/js

echo " - Fetch Material UI"
curl -o resources/public/js/material-ui.production.min.js https://unpkg.com/@material-ui/core@4.4.3/umd/material-ui.production.min.js
# FIXME: do a shasum check here

cp target/public/cljs-out/prod-main.js resources/public/js/main.js
cp target/public/cljs-out/version-info.js resources/public/js/version-info.js
echo "Package artifact"

COMMIT=`git rev-parse HEAD`
cd resources/public
zip -r ../../../../frontend-$COMMIT.zip *
cd ../..

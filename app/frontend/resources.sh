#!/usr/bin/env bash
set -eu

echo "Fetch resources"

mkdir resources/public/js

cp out/main.js resources/public/js/main.js

cp -R ../common/resources/public/language resources/public/

echo "Package artifact"

COMMIT=`git rev-parse HEAD`
cd resources/public
zip -r ../../../../frontend-$CODEBUILD_RESOLVED_SOURCE_VERSION.zip *
cd ../..

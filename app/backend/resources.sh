#!/bin/bash

echo "Fetch resources"

mkdir -p resources/public

echo "Fetch Material UI"
curl -o resources/public/material-ui.production.min.js https://unpkg.com/@material-ui/core@4.3.0/umd/material-ui.production.min.js
# FIXME: do a shasum check here

echo "Fetch frontend build"
cp ../frontend/target/public/cljs-out/prod-main.js resources/public/main.js
cp -r ../frontend/resources/public/language resources/public

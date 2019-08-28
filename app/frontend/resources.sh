#!/bin/bash

echo "Fetch resources"

echo " - Fetch Material UI"
curl -o resources/public/material-ui.production.min.js https://unpkg.com/@material-ui/core@4.3.1/umd/material-ui.production.min.js
# FIXME: do a shasum check here

cp target/public/cljs-out/prod-main.js resources/public/main.js

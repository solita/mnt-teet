#!/bin/bash

clj -m figwheel.main -O advanced -bo prod



cp $WD/resources/public/index.html index.html
cp $WD/target/public/cljs-out/prod-main.js main.js
cp $WD/resources/public/language/en.edn language/en.edn
cp $WD/resources/public/language/et.edn language/et.edn
cp $WD/resources/public/config.json.tpl config.json.tpl

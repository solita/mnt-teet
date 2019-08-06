#!/bin/bash

BUCKET=teis-poc

function upload () {
    aws s3 cp $1 s3://$BUCKET/$2
    aws s3api put-object-acl --bucket $BUCKET --key $2 --acl public-read
}

clj -m figwheel.main -O advanced -bo prod

echo "Prepare artifacts"
WD=`pwd`
cd ../..

mkdir language

cp $WD/resources/public/index.html index.html
cp $WD/target/public/cljs-out/prod-main.js main.js
cp $WD/resources/public/language/en.edn language/en.edn
cp $WD/resources/public/language/et.edn language/et.edn
cp $WD/resources/public/config.json.tpl config.json.tpl

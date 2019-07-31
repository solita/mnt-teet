#!/bin/bash

BUCKET=teis-poc

function upload () {
    aws s3 cp $1 s3://$BUCKET/$2
    aws s3api put-object-acl --bucket $BUCKET --key $2 --acl public-read
}

clj -m figwheel.main -O advanced -bo prod

upload resources/public/index.html index.html
upload target/public/cljs-out/prod-main.js main.js
upload resources/public/language/en.edn language/en.edn
upload resources/public/language/et.edn language/et.edn

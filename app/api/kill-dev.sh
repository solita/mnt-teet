#!/bin/sh

ID=`docker ps | grep mnt-teet/teet-api | cut -f1 -d' '`

docker kill $ID

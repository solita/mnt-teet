#!/bin/sh

clj -A:pack -m mach.pack.alpha.jib \
    --image-name 512288025010.dkr.ecr.eu-central-1.amazonaws.com/teet-service:latest \
    --image-type registry \
    -m teet.main

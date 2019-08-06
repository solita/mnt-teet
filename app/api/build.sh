#!/bin/sh
docker build . -t mnt-teet/teet-api:latest --build-arg POSTGREST_VERSION=v6.0.1

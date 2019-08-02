#!/bin/sh
docker build . -t teis-poc/teis-project-registry:latest --build-arg POSTGREST_VERSION=v6.0.1

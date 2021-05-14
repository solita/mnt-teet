#!/usr/bin/env bash
set -eu

DOCKER_USER=$(aws ssm get-parameters --names "/teet/docker/user" --query "Parameters[0].Value" | tr -d '"')
DOCKER_ACCESS_TOKEN=$(aws ssm get-parameters --names "/teet/docker/token" --query "Parameters[0].Value" | tr -d '"')

echo $DOCKER_USER
echo $DOCKER_ACCESS_TOKEN

$(echo $DOCKER_ACCESS_TOKEN | docker login --username $DOCKER_USER --password-stdin)

docker build . -t mnt-teet/teet-api:latest --build-arg POSTGREST_VERSION=v6.0.1

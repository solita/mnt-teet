#!/usr/bin/env bash
set -eu

echo STARTING PostgREST
postgrest /etc/postgrest.conf

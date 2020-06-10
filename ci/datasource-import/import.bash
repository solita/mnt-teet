#!/usr/bin/env bash

set -euo pipefail

function get-ssm {
    key="$1"
    aws --output text ssm get-parameters --names "$key" \
	                                 --query "Parameters[0].Value"
}
cd app/datasource-import
export TEET_API_URL=$(get-ssm /teet/api/url)
export TEET_API_SECRET=$(get-ssm /teet/api/jwt-secret)
if [ -z "$TEET_API_URL" -o -z "$TEET_API_SECRET" ]; then
    echo empty ssm keys required for import
    exit 1
fi
time clojure -A:import env

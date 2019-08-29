#!/bin/bash

WEBHOOK_URL=`aws ssm get-parameters --names "/teet-dev/slack/webhook-url" --query "Parameters[0].Value"`

# Remove prefix/suffix double quotes
WEBHOOK_URL="${WEBHOOK_URL%\"}"
WEBHOOK_URL="${WEBHOOK_URL#\"}"

PAYLOAD="{\"text\": \"$*\", \"icon_emoji\": \":thisisfine:\"}"

curl -d "$PAYLOAD" $WEBHOOK_URL

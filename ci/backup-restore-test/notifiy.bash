#!/usr/bin/env bash

WEBHOOK_URL=$(aws ssm get-parameters --names "/teet/slack/webhook-url" --query "Parameters[0].Value" | tr -d '"')

MSG=$1
EMOJI=$2

PAYLOAD="{\"text\": \"$MSG\", \"icon_emoji\": \"$EMOJI\"}"

curl -d "$PAYLOAD" $WEBHOOK_URL
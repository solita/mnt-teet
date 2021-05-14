#!/usr/bin/env bash

WEBHOOK_URL=$(aws ssm get-parameters --names "/teet/slack/webhook-url" --query "Parameters[0].Value" | tr -d '"')

echo "WEBHOOK URL:"$WEBHOOK_URL

MSG=$1
EMOJI=$2

echo "MSG:"$MSG
echo "Emoji:"$EMOJI

PAYLOAD="{\"text\": \"$MSG\", \"icon_emoji\": \"$EMOJI\"}"

curl -d "$PAYLOAD" $WEBHOOK_URL
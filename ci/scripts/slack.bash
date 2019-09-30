#!/bin/bash

if [ "$CODEBUILD_BUILD_SUCCEEDING" -eq "1" ]
then
    if [ "$NOTIFY_ON_SUCCESS" -eq "0" ]
    then
       exit
    fi
    EMOJI=":success:"
    MSG="SUCCESS $*"
else
    EMOJI=":thisisfine:"
    MSG="FAILED $*"
fi

# Get Slack webhook URL from parameter store
WEBHOOK_URL=`aws ssm get-parameters --names "/teet-dev/slack/webhook-url" --query "Parameters[0].Value"`

# Remove prefix/suffix double quotes
WEBHOOK_URL="${WEBHOOK_URL%\"}"
WEBHOOK_URL="${WEBHOOK_URL#\"}"

PAYLOAD="{\"text\": \"$MSG\", \"icon_emoji\": \"$EMOJI\"}"

curl -d "$PAYLOAD" $WEBHOOK_URL

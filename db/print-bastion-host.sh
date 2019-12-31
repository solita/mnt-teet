#!/usr/bin/env bash
aws ec2 describe-instances --filters 'Name=tag:Name,Values=SSH*' --output text --query 'Reservations[*].Instances[*].PublicDnsName'

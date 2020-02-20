#!/bin/bash

export PATH="$PATH:/home/runner/work"

clj-kondo --lint app/backend/src app/frontend/src

if [ $? -eq 2 ]; then
    exit 0;
fi

exit $?

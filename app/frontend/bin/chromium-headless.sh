#!/bin/sh

if [ -z "$CHROMIUM_BROWSER" ]; then
    "$CHROMIUM_BROWSER" --headless --no-sandbox --disable-gpu --repl "$@"
elif [ -z "$CHROME" ]; then
    "$CHROME" "$@"
fi

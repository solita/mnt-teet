#!/bin/sh

"$CHROMIUM_BROWSER" --headless --no-sandbox --disable-gpu --repl "$@"

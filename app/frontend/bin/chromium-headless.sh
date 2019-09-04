#!/bin/sh

function check_chromium {
    if ! [ -x "$(command -v "$CHROMIUM_BROWSER")" ]; then
        echo "chromium not found, make sure it is in PATH" >&2
        exit 1
    fi
}

check_chromium

"$CHROMIUM_BROWSER" --headless --disable-gpu --repl "$@"

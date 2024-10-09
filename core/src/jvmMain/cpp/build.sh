#!/bin/bash
set -e

[[ -z "$TARGET" ]] && echo "Please set the PLATFORM variable" && exit 1
[[ -z "$SOURCE_PATH" ]] && echo "Please set the SOURCE_PATH variable" && exit 1
[[ -z "$INTEROP_PATH" ]] && echo "Please set the INTEROP_PATH variable" && exit 1

mkdir -p "$TARGET"
cd "$TARGET"

cmake -DSQLITE3_INTEROP_DIR="$INTEROP_PATH" "$SOURCE_PATH"
cmake --build .
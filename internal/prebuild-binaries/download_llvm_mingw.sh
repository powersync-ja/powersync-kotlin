#!/usr/bin/env bash
set -euo pipefail

mkdir -p build
cd build/

curl -L https://github.com/mstorsjo/llvm-mingw/releases/download/20251118/llvm-mingw-20251118-ucrt-macos-universal.tar.xz -o llvm-ming.tar.xz
tar --extract --gzip --file llvm-ming.tar.xz
rm llvm-ming.tar.xz

mv llvm-mingw-20251118-ucrt-macos-universal llvm-mingw

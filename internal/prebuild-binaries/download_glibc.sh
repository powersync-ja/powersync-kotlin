#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/sysroot
cd build/sysroot

function download_package() {
  curl -L $1 | tar --extract --gzip
}

download_package https://archlinux.org/packages/core/x86_64/glibc/download/
download_package https://archlinux.org/packages/core/x86_64/linux-api-headers/download/
download_package https://archlinux.org/packages/core/x86_64/gcc/download/
download_package https://archlinux.org/packages/core/x86_64/gcc-libs/download/

download_package https://archlinux.org/packages/extra/any/aarch64-linux-gnu-glibc/download/
download_package https://archlinux.org/packages/extra/any/aarch64-linux-gnu-linux-api-headers/download/
download_package https://archlinux.org/packages/extra/x86_64/aarch64-linux-gnu-gcc/download/

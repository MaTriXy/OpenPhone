#!/usr/bin/env bash

set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  exec sudo -E bash "$0" "$@"
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y software-properties-common
add-apt-repository -y universe || true
apt-get update
apt-get install -y \
  bc \
  bison \
  build-essential \
  ccache \
  curl \
  flex \
  g++-multilib \
  gcc-multilib \
  git \
  git-lfs \
  gnupg \
  gperf \
  imagemagick \
  lib32readline-dev \
  lib32z1-dev \
  libdw-dev \
  libelf-dev \
  libgnutls28-dev \
  libsdl1.2-dev \
  libssl-dev \
  libxml2 \
  libxml2-utils \
  lz4 \
  lzop \
  pngcrush \
  protobuf-compiler \
  python3-protobuf \
  python3-venv \
  rsync \
  schedtool \
  squashfs-tools \
  unzip \
  xxd \
  xsltproc \
  zip \
  zlib1g-dev

install -d -m 0755 /usr/local/bin
curl -L --fail --silent --show-error \
  https://storage.googleapis.com/git-repo-downloads/repo \
  -o /usr/local/bin/repo
chmod 0755 /usr/local/bin/repo

git lfs install --system

install -d -m 0775 -o ubuntu -g ubuntu /opt/openphone-build

if command -v timedatectl >/dev/null 2>&1; then
  timedatectl set-timezone UTC || true
fi

printf 'OpenPhone Android build host bootstrap complete.\n'

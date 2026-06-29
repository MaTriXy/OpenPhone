#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: scripts/bootstrap-android-build-host.sh

Installs Ubuntu packages and tools required for Android build hosts.
USAGE
}

case "${1:-}" in
  -h|--help)
    usage
    exit 0
    ;;
  "")
    ;;
  *)
    usage >&2
    printf 'error: unknown argument: %s\n' "$1" >&2
    exit 1
    ;;
esac

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
  openjdk-17-jdk \
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

install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
  | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
chmod 0644 /etc/apt/keyrings/nodesource.gpg
printf 'deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_22.x nodistro main\n' \
  > /etc/apt/sources.list.d/nodesource.list
apt-get update
apt-get install -y nodejs

install -d -m 0755 /usr/local/bin
curl -L --fail --silent --show-error \
  https://storage.googleapis.com/git-repo-downloads/repo \
  -o /usr/local/bin/repo
chmod 0755 /usr/local/bin/repo

git lfs install --system

install -d -m 0775 /opt/openphone-build
if [[ -n "${SUDO_UID:-}" && -n "${SUDO_GID:-}" ]]; then
  chown "$SUDO_UID:$SUDO_GID" /opt/openphone-build
fi

if command -v timedatectl >/dev/null 2>&1; then
  timedatectl set-timezone UTC || true
fi

printf 'OpenPhone Android build host bootstrap complete.\n'

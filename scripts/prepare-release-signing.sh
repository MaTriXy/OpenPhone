#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  scripts/prepare-release-signing.sh --keys-dir <private-dir> [--android-dir <path>]

Creates a private OpenPhone release-signing workspace outside the repository.

The script writes:
  README.md
  .gitignore
  key-map.txt

If --android-dir points at a synced Android tree with development/tools/make_key,
it also prints the exact key-generation commands to run from that tree.
EOF
}

keys_dir=""
android_dir=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --keys-dir)
      keys_dir="${2:-}"
      shift 2
      ;;
    --android-dir)
      android_dir="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$keys_dir" ]]; then
  usage >&2
  exit 2
fi

mkdir -p "$keys_dir"
keys_dir="$(cd "$keys_dir" && pwd)"

case "$keys_dir" in
  "$root"/*|"$root")
    printf 'release signing keys must live outside the OpenPhone repository: %s\n' "$keys_dir" >&2
    exit 1
    ;;
esac

cat > "$keys_dir/.gitignore" <<'EOF'
*
!.gitignore
!README.md
!key-map.txt
EOF

cat > "$keys_dir/key-map.txt" <<'EOF'
# OpenPhone release signing key mapping for Android releasetools.
#
# Use these keys with sign_target_files_apks / ota_from_target_files from a
# private build environment. Do not commit generated .pk8/.pem files.

build/make/target/product/security/testkey=releasekey
build/make/target/product/security/devkey=releasekey
build/make/target/product/security/releasekey=releasekey
build/make/target/product/security/platform=platform
build/make/target/product/security/shared=shared
build/make/target/product/security/media=media
build/make/target/product/security/networkstack=networkstack
build/make/target/product/security/sdk_sandbox=sdk_sandbox
build/make/target/product/security/bluetooth=bluetooth
EOF

cat > "$keys_dir/README.md" <<'EOF'
# OpenPhone Private Release Signing Workspace

This directory is intentionally outside the OpenPhone repository. It is for
private Android release signing keys only.

Expected key stems:

- `releasekey`
- `platform`
- `shared`
- `media`
- `networkstack`
- `sdk_sandbox`
- `bluetooth`

Each key stem should have:

- `<stem>.pk8`
- `<stem>.x509.pem`

Never commit these files. Keep this directory encrypted, backed up, and access
controlled.
EOF

printf 'Prepared private signing workspace: %s\n' "$keys_dir"

if [[ -n "$android_dir" ]]; then
  android_dir="$(cd "$android_dir" && pwd)"
  make_key="$android_dir/development/tools/make_key"
  if [[ ! -x "$make_key" ]]; then
    printf 'Android make_key not found or not executable: %s\n' "$make_key" >&2
    exit 1
  fi
  cat <<EOF

Run from the Android tree to generate private keys:

cd "$android_dir"
for key in releasekey platform shared media networkstack sdk_sandbox bluetooth; do
  development/tools/make_key "$keys_dir/\$key" "/CN=OpenPhone \$key/"
done

Use "$keys_dir/key-map.txt" with releasetools when signing target-files.
EOF
fi

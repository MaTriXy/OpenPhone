#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  scripts/sign-release-ota.sh \
    --android-dir <path> \
    --keys-dir <private-dir> \
    --target-files <target_files.zip> \
    --output-dir <dir> \
    [--name <artifact-prefix>] \
    [--dry-run]

Signs Android target-files with private OpenPhone release keys and generates a
signed OTA ZIP. Private keys must live outside the OpenPhone repository.

Expected files in --keys-dir:
  key-map.txt
  releasekey.pk8 / releasekey.x509.pem
  platform.pk8 / platform.x509.pem
  shared.pk8 / shared.x509.pem
  media.pk8 / media.x509.pem
  networkstack.pk8 / networkstack.x509.pem
  sdk_sandbox.pk8 / sdk_sandbox.x509.pem
  bluetooth.pk8 / bluetooth.x509.pem
EOF
}

android_dir=""
keys_dir=""
target_files=""
output_dir=""
name="openphone-release"
dry_run=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --android-dir)
      android_dir="${2:-}"
      shift 2
      ;;
    --keys-dir)
      keys_dir="${2:-}"
      shift 2
      ;;
    --target-files)
      target_files="${2:-}"
      shift 2
      ;;
    --output-dir)
      output_dir="${2:-}"
      shift 2
      ;;
    --name)
      name="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run=true
      shift
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

if [[ -z "$android_dir" || -z "$keys_dir" || -z "$target_files" || -z "$output_dir" ]]; then
  usage >&2
  exit 2
fi

android_dir="$(cd "$android_dir" && pwd)"
keys_dir="$(cd "$keys_dir" && pwd)"
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"

case "$keys_dir" in
  "$root"/*|"$root")
    printf 'release signing keys must live outside the OpenPhone repository: %s\n' "$keys_dir" >&2
    exit 1
    ;;
esac

sign_target_files="$android_dir/build/make/tools/releasetools/sign_target_files_apks"
ota_from_target_files="$android_dir/build/make/tools/releasetools/ota_from_target_files"
key_map="$keys_dir/key-map.txt"
signed_target_files="$output_dir/${name}-signed-target_files.zip"
signed_ota="$output_dir/${name}-signed-ota.zip"

[[ -f "$target_files" || "$dry_run" == true ]] || {
  printf 'missing target-files zip: %s\n' "$target_files" >&2
  exit 1
}
[[ -f "$key_map" ]] || {
  printf 'missing signing key map: %s\n' "$key_map" >&2
  exit 1
}
[[ -x "$sign_target_files" || "$dry_run" == true ]] || {
  printf 'missing sign_target_files_apks: %s\n' "$sign_target_files" >&2
  exit 1
}
[[ -x "$ota_from_target_files" || "$dry_run" == true ]] || {
  printf 'missing ota_from_target_files: %s\n' "$ota_from_target_files" >&2
  exit 1
}

required_keys=(releasekey platform shared media networkstack sdk_sandbox bluetooth)
for key in "${required_keys[@]}"; do
  if [[ "$dry_run" == false ]]; then
    [[ -f "$keys_dir/$key.pk8" ]] || {
      printf 'missing private key: %s\n' "$keys_dir/$key.pk8" >&2
      exit 1
    }
    [[ -f "$keys_dir/$key.x509.pem" ]] || {
      printf 'missing public certificate: %s\n' "$keys_dir/$key.x509.pem" >&2
      exit 1
    }
  fi
done

key_args=()
while IFS= read -r line; do
  line="${line%%#*}"
  line="${line//[[:space:]]/}"
  [[ -n "$line" ]] || continue
  key_args+=("-k" "$line")
done < "$key_map"

cmd_sign=(
  "$sign_target_files"
  -o
  -d "$keys_dir"
  "${key_args[@]}"
  "$target_files"
  "$signed_target_files"
)
cmd_ota=(
  "$ota_from_target_files"
  -k "$keys_dir/releasekey"
  "$signed_target_files"
  "$signed_ota"
)

if [[ "$dry_run" == true ]]; then
  printf 'DRY RUN sign target-files command:\n'
  printf '  %q' "${cmd_sign[@]}"
  printf '\nDRY RUN signed OTA command:\n'
  printf '  %q' "${cmd_ota[@]}"
  printf '\n'
  exit 0
fi

"${cmd_sign[@]}"
"${cmd_ota[@]}"
sha256sum "$signed_ota" > "$signed_ota.sha256"

printf 'Signed target-files: %s\n' "$signed_target_files"
printf 'Signed OTA: %s\n' "$signed_ota"
printf 'Signed OTA SHA-256: %s\n' "$signed_ota.sha256"

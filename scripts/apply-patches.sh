#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

need_cmd git

[[ -d "$OPENPHONE_ANDROID_DIR/.repo" ]] || die "Android tree not initialized: $OPENPHONE_ANDROID_DIR"

info "copying OpenPhone overlay into Android tree"
rm -rf \
  "$OPENPHONE_ANDROID_DIR/packages/apps/OpenPhoneAssistant" \
  "$OPENPHONE_ANDROID_DIR/vendor/openphone"
cp -R "$OPENPHONE_ROOT/overlay/." "$OPENPHONE_ANDROID_DIR/"

shopt -s nullglob

for patch_dir in "$OPENPHONE_ROOT"/patches/*; do
  [[ -d "$patch_dir" ]] || continue
  patch_name="$(basename "$patch_dir")"
  repo_path="$(android_path_for_patch_dir "$patch_name")"
  target_dir="$OPENPHONE_ANDROID_DIR/$repo_path"
  patches=("$patch_dir"/*.patch)

  if [[ ${#patches[@]} -eq 0 ]]; then
    continue
  fi

  [[ -d "$target_dir/.git" ]] || die "patch target is not a git repo: $repo_path"

  info "applying patches for $repo_path"
  (
    cd "$target_dir"
    for patch in "${patches[@]}"; do
      subject="$(
        awk '
          /^Subject: / {
            capture = 1
            s = $0
            sub(/^Subject: /, "", s)
            sub(/^\[PATCH[^]]*\] /, "", s)
            next
          }
          capture && /^[ \t]/ {
            line = $0
            sub(/^[ \t]+/, "", line)
            s = s " " line
            next
          }
          capture {
            print s
            exit
          }
        ' "$patch"
      )"
      if [[ -n "$subject" ]]; then
        log_subjects="$(mktemp)"
        git log --format=%s -n 200 > "$log_subjects"
        if grep -Fxq "$subject" "$log_subjects"; then
          rm -f "$log_subjects"
          info "skipping already-applied patch $(basename "$patch")"
          continue
        fi
        rm -f "$log_subjects"
      fi
      info "git am $(basename "$patch")"
      if ! git am "$patch"; then
        git am --abort >/dev/null 2>&1 || true
        info "git apply --recount $(basename "$patch")"
        git apply --recount "$patch"
        git add -A
        git -c user.name="OpenPhone" -c user.email="openphone@example.invalid" \
          commit -m "$subject"
      fi
    done
  )
done

info "overlay and patches applied"

#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
template="$root/services/model-broker/deploy/nginx-openphone-model-broker.conf"

usage() {
  cat <<'EOF'
Usage:
  scripts/setup-model-broker-tls.sh --domain <fqdn> --email <email> [--output <path>] [--apply]

Prepares nginx TLS configuration for the OpenPhone model broker and prints the
certbot commands required for certificate issuance and renewal validation.

Without --apply, the script writes the rendered nginx config to --output or
stdout and prints the commands to stderr. With --apply, it writes the nginx
site under /etc/nginx and runs certbot/nginx reload commands.
EOF
}

domain=""
email=""
output=""
apply=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain)
      domain="${2:-}"
      shift 2
      ;;
    --email)
      email="${2:-}"
      shift 2
      ;;
    --output)
      output="${2:-}"
      shift 2
      ;;
    --apply)
      apply=true
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

if [[ -z "$domain" || -z "$email" ]]; then
  usage >&2
  exit 2
fi

if [[ "$domain" != *.* || "$domain" == *"/"* || "$domain" == *" "* ]]; then
  printf 'domain must be a fully qualified domain name, got: %s\n' "$domain" >&2
  exit 2
fi

if [[ "$email" != *@*.* || "$email" == *" "* ]]; then
  printf 'email must look like an email address, got: %s\n' "$email" >&2
  exit 2
fi

rendered="$(mktemp)"
sed "s/broker\\.openphone\\.example/$domain/g" "$template" > "$rendered"

site_available="/etc/nginx/sites-available/openphone-model-broker.conf"
site_enabled="/etc/nginx/sites-enabled/openphone-model-broker.conf"

if [[ "$apply" == true ]]; then
  if [[ "$(id -u)" -ne 0 ]]; then
    printf '--apply must be run as root\n' >&2
    rm -f "$rendered"
    exit 1
  fi
  install -m 0644 "$rendered" "$site_available"
  ln -sfn "$site_available" "$site_enabled"
  certbot --nginx -d "$domain" --non-interactive --agree-tos -m "$email" --redirect
  nginx -t
  systemctl reload nginx
  certbot renew --dry-run
else
  if [[ -n "$output" ]]; then
    cp "$rendered" "$output"
    printf 'Rendered nginx config: %s\n' "$output" >&2
  else
    cat "$rendered"
  fi
  {
    printf '\nReview DNS first: %s must point at this host.\n' "$domain"
    printf 'Then run as root:\n'
    printf '  install -m 0644 <rendered-config> %s\n' "$site_available"
    printf '  ln -sfn %s %s\n' "$site_available" "$site_enabled"
    printf '  certbot --nginx -d %s --non-interactive --agree-tos -m %s --redirect\n' "$domain" "$email"
    printf '  nginx -t && systemctl reload nginx\n'
    printf '  certbot renew --dry-run\n'
  } >&2
fi

rm -f "$rendered"

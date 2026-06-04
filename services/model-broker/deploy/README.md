# Model Broker Deployment

This directory contains a first Linux deployment path for the OpenPhone model
broker. It is intended for hosted development services, not final production.

## Files

- `openphone-model-broker.service`: hardened systemd unit.
- `openphone-model-broker.env.example`: environment template with no secrets.
- `nginx-openphone-model-broker.conf`: TLS reverse-proxy template.

## Install

Create a restricted service user:

```bash
sudo useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin openphone-broker
```

Install the repository and configuration:

```bash
sudo mkdir -p /opt/openphone /etc/openphone /var/log/openphone
sudo rsync -a --delete ./ /opt/openphone/
sudo cp services/model-broker/providers.example.json /etc/openphone/providers.json
sudo cp services/model-broker/devices.example.json /etc/openphone/devices.json
sudo cp services/model-broker/deploy/openphone-model-broker.env.example \
  /etc/openphone/model-broker.env
sudo chown -R root:root /opt/openphone /etc/openphone
sudo chown openphone-broker:openphone-broker /var/log/openphone
sudo chmod 0600 /etc/openphone/model-broker.env
```

Edit `/etc/openphone/model-broker.env` and fill in real values for
`OPENAI_API_KEY`, `OPENPHONE_BROKER_TOKEN_SECRET`, and
`OPENPHONE_BROKER_ADMIN_TOKENS`. If `/etc/openphone/devices.json` references
per-device attestation secret environment variables, fill those values too.

Install and start the service:

```bash
sudo cp services/model-broker/deploy/openphone-model-broker.service \
  /etc/systemd/system/openphone-model-broker.service
sudo systemctl daemon-reload
sudo systemctl enable --now openphone-model-broker.service
```

The broker should bind to `127.0.0.1:8787`. Put it behind a TLS-terminating
reverse proxy and expose only HTTPS to phones.

## TLS Reverse Proxy

Install nginx and render the template for your broker domain:

```bash
scripts/setup-model-broker-tls.sh \
  --domain broker.example.com \
  --email ops@example.com \
  --output /tmp/openphone-model-broker.conf
```

Review DNS first: the broker domain must point at the host. Then install the
rendered config and request a certificate:

```bash
sudo install -m 0644 /tmp/openphone-model-broker.conf \
  /etc/nginx/sites-available/openphone-model-broker.conf
sudo ln -sfn /etc/nginx/sites-available/openphone-model-broker.conf \
  /etc/nginx/sites-enabled/openphone-model-broker.conf
sudo certbot --nginx -d broker.example.com \
  --non-interactive --agree-tos -m ops@example.com --redirect
```

The helper can also apply the config and run certbot directly:

```bash
sudo scripts/setup-model-broker-tls.sh \
  --domain broker.example.com \
  --email ops@example.com \
  --apply
```

The template redirects HTTP to HTTPS, proxies only `/healthz` and `/v1/`, sets
HSTS/no-store headers, keeps the Python broker bound to localhost, and aligns
`client_max_body_size` with the default 15 MiB broker request limit.

Validate and reload:

```bash
sudo nginx -t
sudo systemctl reload nginx
sudo certbot renew --dry-run
```

## Rotate Broker Secrets

Generate fresh values without writing a file:

```bash
scripts/rotate-model-broker-secrets.sh --print-only
```

Rotate the deployed broker token-signing secret and admin token:

```bash
sudo /opt/openphone/scripts/rotate-model-broker-secrets.sh \
  --env-file /etc/openphone/model-broker.env \
  --restart-service
```

The script updates only `OPENPHONE_BROKER_TOKEN_SECRET` and
`OPENPHONE_BROKER_ADMIN_TOKENS`, preserves provider keys and deployment
settings, writes a timestamped backup next to the env file, and chmods the env
file to `0600`. Existing signed session tokens stop validating after the token
secret changes, so mint fresh device session tokens after rotation.

Rotate the deployed OpenAI provider key after creating a replacement key in
the provider console:

```bash
sudo /opt/openphone/scripts/rotate-model-broker-secrets.sh \
  --env-file /etc/openphone/model-broker.env \
  --provider-key sk-new-provider-key \
  --restart-service
```

Provider-key mode updates only `OPENAI_API_KEY` and preserves broker token
secrets, admin tokens, and deployment settings.

## Hardening Notes

- Keep provider and admin secrets only in `/etc/openphone/model-broker.env`.
- Keep provider and device registries in `/etc/openphone`.
- Require per-device development attestation secrets for hosted token
  issuance by adding `attestation_secret_env` to device registry entries and
  setting the matching secret in `/etc/openphone/model-broker.env`.
- Bind the broker to localhost and terminate TLS in the reverse proxy.
- Set both request-count and byte-volume limits. The default
  `OPENPHONE_BROKER_MAX_BYTES_PER_MINUTE=62914560` allows short development
  bursts while still bounding repeated screenshot/audio uploads per token
  subject.
- Rotate `OPENPHONE_BROKER_ADMIN_TOKENS`, `OPENPHONE_BROKER_TOKEN_SECRET`, and
  provider keys regularly. Use `scripts/rotate-model-broker-secrets.sh` for the
  env-file update; create/revoke provider keys through the provider console.
- Ship logs to a secured backend from `/var/log/openphone/model-broker.jsonl`.
- The broker intentionally does not log request or response bodies.
- Keep `OPENPHONE_BROKER_REQUIRE_OPENPHONE_METADATA=true` and
  `OPENPHONE_BROKER_REJECT_SENSITIVE_SCREEN=true` for hosted development
  brokers. This rejects non-OpenPhone-shaped Responses requests and blocks
  sensitive-screen risk flags before provider forwarding.
- Keep `OPENPHONE_BROKER_MAX_IMAGES_PER_REQUEST=1` until the agent loop has a
  reviewed need for multi-image context.
- Keep provider retries low and bounded. The default
  `OPENPHONE_BROKER_PROVIDER_MAX_RETRIES=1` only retries transient 429/5xx
  provider errors once and records `provider_attempts` in audit events.
- Treat HMAC device proofs as a development gate only. Replace them with
  hardware-backed attestation before commercial production use.

#!/usr/bin/env bash

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

node "$root/runtime/protocol/validate-runtime-protocol.mjs"
node --check "$root/runtime/protocol/openphone-runtime-tools.mjs"
node --check "$root/integrations/adb/openphone-adb-transport.mjs"
node --check "$root/integrations/cli/src/index.mjs"
node --check "$root/integrations/mcp-server/src/index.mjs"
node "$root/integrations/openclaw-plugin/tests/policy-contract.mjs"
node "$root/integrations/cli/tests/cli-contract.mjs"
node "$root/integrations/mcp-server/tests/protocol-contract.mjs"

printf 'Runtime protocol checks passed.\n'

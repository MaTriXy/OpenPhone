#!/usr/bin/env node

import assert from "node:assert/strict";
import { main } from "../src/index.mjs";

function captureIo() {
  let stdout = "";
  let stderr = "";
  return {
    io: {
      stdout: { write(value) { stdout += value; } },
      stderr: { write(value) { stderr += value; } },
    },
    stdout() {
      return stdout;
    },
    stderr() {
      return stderr;
    },
  };
}

{
  const capture = captureIo();
  const code = await main(["tool", "list", "--json"], capture.io);
  assert.equal(code, 0);
  const parsed = JSON.parse(capture.stdout());
  assert.ok(parsed.tools.some((tool) => tool.name === "openphone.screen.get"));
  assert.equal(capture.stderr(), "");
}

{
  const capture = captureIo();
  const code = await main([
    "tool",
    "invoke",
    "openphone.screen.get",
    "{\"include_screenshot\":false}",
    "--dry-run",
    "--json",
  ], capture.io);
  assert.equal(code, 0);
  const parsed = JSON.parse(capture.stdout());
  assert.equal(parsed.dry_run, true);
  assert.equal(parsed.command, "openphone.screen.get");
}

{
  const capture = captureIo();
  const code = await main([
    "runtime",
    "select",
    "--chat",
    "openclaw",
    "--volume",
    "phone",
    "--dry-run",
    "--json",
  ], capture.io);
  assert.equal(code, 0);
  const parsed = JSON.parse(capture.stdout());
  assert.deepEqual(parsed.changes, {
    chat_runtime: "openclaw",
    volume_runtime: "builtin",
  });
}

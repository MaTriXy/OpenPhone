#!/usr/bin/env node

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { pathToFileURL } from "node:url";

const pluginRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const distPath = path.join(pluginRoot, "dist/index.js");
const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "openphone-openclaw-plugin-"));

function fail(message) {
  console.error(`openclaw-plugin policy smoke: ${message}`);
  process.exit(1);
}

try {
  const packageDir = path.join(tempDir, "node_modules/openclaw");
  const sdkDir = path.join(packageDir, "plugin-sdk");
  fs.mkdirSync(sdkDir, { recursive: true });
  fs.writeFileSync(path.join(packageDir, "package.json"), JSON.stringify({
    name: "openclaw",
    type: "module",
    exports: {
      "./plugin-sdk/plugin-entry": "./plugin-sdk/plugin-entry.js",
    },
  }));
  fs.writeFileSync(path.join(sdkDir, "plugin-entry.js"), [
    "export function definePluginEntry(entry) {",
    "  return entry;",
    "}",
    "",
  ].join("\n"));

  const tempPluginPath = path.join(tempDir, "index.js");
  fs.copyFileSync(distPath, tempPluginPath);
  const plugin = (await import(pathToFileURL(tempPluginPath).href)).default;
  if (!plugin || plugin.id !== "openphone-android" || typeof plugin.register !== "function") {
    fail("plugin entry did not export the expected OpenPhone plugin");
  }

  const policies = [];
  plugin.register({
    registerNodeInvokePolicy(policy) {
      policies.push(policy);
    },
  });

  if (policies.length !== 3) {
    fail(`expected 3 node invoke policies, got ${policies.length}`);
  }
  const safeRead = policies.find((policy) =>
    Array.isArray(policy.defaultPlatforms) && !policy.dangerous);
  const privateRead = policies.find((policy) =>
    policy.dangerous && policy.commands.includes("openphone.device.status"));
  const actions = policies.find((policy) =>
    policy.dangerous && policy.commands.includes("openphone.app.open"));

  if (!safeRead || !safeRead.commands.includes("openphone.screen.get")
      || !safeRead.commands.includes("openphone.apps.search")) {
    fail("safe read policy is missing expected commands");
  }
  if (!privateRead || !privateRead.commands.includes("openphone.messages.search")) {
    fail("private read policy is missing expected commands");
  }
  if (!actions || !actions.commands.includes("openphone.ui.tap")
      || !actions.commands.includes("openphone.messages.send")) {
    fail("action policy is missing expected commands");
  }
} finally {
  fs.rmSync(tempDir, { recursive: true, force: true });
}

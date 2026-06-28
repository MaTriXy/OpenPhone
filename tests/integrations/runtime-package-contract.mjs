#!/usr/bin/env node

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { pathToFileURL } from "node:url";

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");
const sourceFiles = [
  "adb/openphone-adb-transport.mjs",
  "runtime/protocol/openphone-commands.json",
  "runtime/protocol/openphone-runtime-tools.mjs",
];

const packages = [
  {
    name: "@openphone/runtime-cli",
    root: path.join(repoRoot, "integrations/cli"),
    entry: "src/index.mjs",
    expectedFiles: [
      "package/adb/openphone-adb-transport.mjs",
      "package/runtime/protocol/openphone-commands.json",
      "package/runtime/protocol/openphone-runtime-tools.mjs",
      "package/src/index.mjs",
    ],
  },
  {
    name: "@openphone/runtime-mcp-server",
    root: path.join(repoRoot, "integrations/mcp-server"),
    entry: "src/index.mjs",
    expectedFiles: [
      "package/adb/openphone-adb-transport.mjs",
      "package/runtime/protocol/openphone-commands.json",
      "package/runtime/protocol/openphone-runtime-tools.mjs",
      "package/src/index.mjs",
    ],
  },
];

for (const pkg of packages) {
  for (const sourceFile of sourceFiles) {
    const sourcePath = path.join(repoRoot, sourcePathForPackageFile(sourceFile));
    const packagePath = path.join(pkg.root, sourceFile);
    assert.equal(
      fs.readFileSync(packagePath, "utf8"),
      fs.readFileSync(sourcePath, "utf8"),
      `${pkg.name} ${sourceFile} must match ${sourcePathForPackageFile(sourceFile)}`,
    );
  }

  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), "openphone-runtime-package-"));
  try {
    const packOutput = execFileSync("npm", [
      "pack",
      "--json",
      "--pack-destination",
      tempDir,
    ], {
      cwd: pkg.root,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "inherit"],
    });
    const [packed] = JSON.parse(packOutput);
    assert.equal(packed.name, pkg.name);
    const tarball = path.join(tempDir, packed.filename);
    execFileSync("tar", ["-xzf", tarball, "-C", tempDir], {
      stdio: ["ignore", "pipe", "inherit"],
    });
    for (const expected of pkg.expectedFiles) {
      assert.ok(fs.existsSync(path.join(tempDir, expected)), `${pkg.name} missing ${expected}`);
    }
    const entryUrl = pathToFileURL(path.join(tempDir, "package", pkg.entry)).href;
    const module = await import(entryUrl);
    assert.ok(module, `${pkg.name} packed entry did not import`);
  } finally {
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
}

function sourcePathForPackageFile(file) {
  if (file.startsWith("adb/")) {
    return `integrations/${file}`;
  }
  return file;
}

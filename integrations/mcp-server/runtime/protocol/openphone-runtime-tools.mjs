import fs from "node:fs";
import path from "node:path";

const protocolRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");
const commandsPath = path.join(protocolRoot, "runtime/protocol/openphone-commands.json");

export function loadCommandManifest(file = commandsPath) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

export function loadCommands(file = commandsPath) {
  const manifest = loadCommandManifest(file);
  if (manifest.version !== 1 || !Array.isArray(manifest.commands)) {
    throw new Error("openphone-commands.json must contain version=1 and commands[]");
  }
  return manifest.commands;
}

export function commandMap(commands = loadCommands()) {
  const out = new Map();
  for (const command of commands) {
    out.set(command.name, command);
    for (const alias of command.aliases ?? []) {
      out.set(alias, command);
    }
  }
  return out;
}

export function resolveCommand(name, commands = loadCommands()) {
  return commandMap(commands).get(name);
}

export function mcpTools(commands = loadCommands()) {
  return commands
    .filter((command) => command.mcp)
    .map((command) => ({
      name: command.name,
      title: titleForCommand(command.name),
      description: command.description,
      inputSchema: command.input_schema ?? { type: "object" },
      outputSchema: command.output_schema ?? { type: "object", additionalProperties: true },
      annotations: {
        readOnlyHint: command.default_exposure !== "dangerous",
        destructiveHint: command.confirmation === "always",
        openWorldHint: command.category === "apps" || command.category === "share",
      },
    }));
}

export function parseJsonObject(value, label = "JSON") {
  if (value == null || value === "") {
    return {};
  }
  let parsed;
  try {
    parsed = JSON.parse(value);
  } catch (error) {
    throw new Error(`${label} must be valid JSON: ${error.message}`);
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error(`${label} must be a JSON object`);
  }
  return parsed;
}

export function textContent(value) {
  const text = typeof value === "string" ? value : JSON.stringify(value, null, 2);
  return [{ type: "text", text }];
}

export function titleForCommand(name) {
  return String(name)
    .replace(/^openphone\./, "")
    .split(/[._-]+/gu)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

#!/usr/bin/env node

import { OpenPhoneAdbTransport } from "../../adb/openphone-adb-transport.mjs";
import {
  loadCommands,
  mcpTools,
  resolveCommand,
  textContent,
} from "../../../runtime/protocol/openphone-runtime-tools.mjs";

const PROTOCOL_VERSION = "2025-11-25";
const SERVER_INFO = { name: "openphone-runtime", version: "0.1.0" };

const commands = loadCommands();

export async function handleRequest(message, context = {}) {
  if (!message || typeof message !== "object") {
    return errorResponse(null, -32600, "Invalid Request");
  }
  if (message.method?.startsWith("notifications/")) {
    return null;
  }
  const id = message.id ?? null;
  const transport = context.transport ?? new OpenPhoneAdbTransport();
  try {
    switch (message.method) {
      case "initialize":
        return resultResponse(id, {
          protocolVersion: PROTOCOL_VERSION,
          capabilities: {
            tools: { listChanged: false },
          },
          serverInfo: SERVER_INFO,
        });
      case "ping":
        return resultResponse(id, {});
      case "tools/list":
        return resultResponse(id, { tools: mcpTools(commands) });
      case "tools/call":
        return resultResponse(id, await callTool(message.params ?? {}, transport));
      default:
        return errorResponse(id, -32601, `Method not found: ${message.method ?? ""}`);
    }
  } catch (error) {
    return errorResponse(id, -32000, error.message);
  }
}

export async function serve(options = {}) {
  const transport = options.transport ?? new OpenPhoneAdbTransport();
  let buffer = Buffer.alloc(0);
  process.stdin.on("data", async (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    const parsed = parseMessages(buffer);
    buffer = parsed.remaining;
    for (const message of parsed.messages) {
      const response = await handleRequest(message, { transport });
      if (response) {
        writeMessage(response);
      }
    }
  });
  process.stdin.resume();
}

async function callTool(params, transport) {
  const name = params.name;
  if (!name || typeof name !== "string") {
    return {
      isError: true,
      content: textContent({ error: { code: "missing_tool_name", message: "Tool name is required." } }),
    };
  }
  const command = resolveCommand(name, commands);
  if (!command || !command.mcp) {
    return {
      isError: true,
      content: textContent({ error: { code: "unknown_tool", message: `Unknown OpenPhone tool: ${name}` } }),
    };
  }
  const result = transport.invoke(name, params.arguments ?? {});
  return {
    isError: result?.ok === false,
    content: textContent(result),
    structuredContent: result,
  };
}

function resultResponse(id, result) {
  return { jsonrpc: "2.0", id, result };
}

function errorResponse(id, code, message) {
  return {
    jsonrpc: "2.0",
    id,
    error: { code, message },
  };
}

function parseMessages(buffer) {
  const messages = [];
  let rest = buffer;
  while (rest.length > 0) {
    const headerEnd = rest.indexOf("\r\n\r\n");
    if (headerEnd >= 0) {
      const header = rest.slice(0, headerEnd).toString("utf8");
      const match = header.match(/content-length:\s*(\d+)/iu);
      if (!match) {
        break;
      }
      const length = Number(match[1]);
      const bodyStart = headerEnd + 4;
      const bodyEnd = bodyStart + length;
      if (rest.length < bodyEnd) {
        break;
      }
      messages.push(JSON.parse(rest.slice(bodyStart, bodyEnd).toString("utf8")));
      rest = rest.slice(bodyEnd);
      continue;
    }

    const newline = rest.indexOf("\n");
    if (newline < 0) {
      break;
    }
    const line = rest.slice(0, newline).toString("utf8").trim();
    rest = rest.slice(newline + 1);
    if (line) {
      messages.push(JSON.parse(line));
    }
  }
  return { messages, remaining: rest };
}

function writeMessage(message) {
  const body = JSON.stringify(message);
  process.stdout.write(`Content-Length: ${Buffer.byteLength(body, "utf8")}\r\n\r\n${body}`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  await serve();
}

#!/usr/bin/env node

import assert from "node:assert/strict";
import { handleRequest } from "../../integrations/mcp-server/src/index.mjs";

const calls = [];
const fakeTransport = {
  invoke(name, args) {
    calls.push({ name, args });
    return { ok: true, name, args };
  },
};

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {},
  }, { transport: fakeTransport });
  assert.equal(response.result.serverInfo.name, "openphone-runtime");
  assert.ok(response.result.capabilities.tools);
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 2,
    method: "ping",
    params: {},
  }, { transport: fakeTransport });
  assert.deepEqual(response.result, {});
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 3,
    method: "tools/list",
    params: {},
  }, { transport: fakeTransport });
  assert.ok(response.result.tools.length > 10);
  assert.ok(response.result.tools.some((tool) => tool.name === "openphone.screen.get"));
  assert.ok(response.result.tools.every((tool) =>
    tool.inputSchema?.type === "object"
      && tool.outputSchema?.type === "object"
      && typeof tool.annotations?.readOnlyHint === "boolean"
      && typeof tool.annotations?.destructiveHint === "boolean"));
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 4,
    method: "tools/call",
    params: {
      name: "openphone.screen.get",
      arguments: { include_screenshot: false },
    },
  }, { transport: fakeTransport });
  assert.equal(response.result.isError, false);
  assert.equal(response.result.structuredContent.name, "openphone.screen.get");
  assert.deepEqual(calls.at(-1), {
    name: "openphone.screen.get",
    args: { include_screenshot: false },
  });
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 5,
    method: "tools/call",
    params: {
      name: "canvas.snapshot",
      arguments: { include_screenshot: true },
    },
  }, { transport: fakeTransport });
  assert.equal(response.result.isError, false);
  assert.deepEqual(calls.at(-1), {
    name: "canvas.snapshot",
    args: { include_screenshot: true },
  });
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 6,
    method: "tools/call",
    params: {
      name: "openphone.unknown",
      arguments: {},
    },
  }, { transport: fakeTransport });
  assert.equal(response.result.isError, true);
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 7,
    method: "runtime/list",
    params: {},
  }, { transport: fakeTransport });
  assert.equal(response.error.code, -32601);
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    method: "notifications/initialized",
    params: {},
  }, { transport: fakeTransport });
  assert.equal(response, null);
}

{
  const response = await handleRequest("not an object", { transport: fakeTransport });
  assert.equal(response.error.code, -32600);
}

#!/usr/bin/env node

import assert from "node:assert/strict";
import { handleRequest } from "../src/index.mjs";

const fakeTransport = {
  invoke(name, args) {
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
    method: "tools/list",
    params: {},
  }, { transport: fakeTransport });
  assert.ok(response.result.tools.some((tool) => tool.name === "openphone.screen.get"));
  assert.ok(response.result.tools.every((tool) => tool.inputSchema));
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 3,
    method: "tools/call",
    params: {
      name: "openphone.screen.get",
      arguments: { include_screenshot: false },
    },
  }, { transport: fakeTransport });
  assert.equal(response.result.isError, false);
  assert.equal(response.result.structuredContent.name, "openphone.screen.get");
}

{
  const response = await handleRequest({
    jsonrpc: "2.0",
    id: 4,
    method: "tools/call",
    params: {
      name: "openphone.unknown",
      arguments: {},
    },
  }, { transport: fakeTransport });
  assert.equal(response.result.isError, true);
}

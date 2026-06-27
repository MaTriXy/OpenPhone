import {
  definePluginEntry,
  type OpenClawPluginNodeInvokePolicyContext,
  type OpenClawPluginNodeInvokePolicyResult,
} from "openclaw/plugin-sdk/plugin-entry";

const OPENPHONE_SCREEN_READ_COMMANDS = [
  "openphone.screen.get",
  "openphone.local.screen_understanding",
];

const OPENPHONE_SAFE_READ_COMMANDS = [
  "openphone.jobs.list",
];

const OPENPHONE_ACTION_COMMANDS = [
  "notifications.open",
  "calendar.add",
  "calendar.update",
  "calendar.delete",
  "sms.draft",
  "sms.send",
  "calls.place",
  "openphone.app.open",
  "openphone.url.open",
  "openphone.ui.tap",
  "openphone.ui.tap_element",
  "openphone.ui.long_press",
  "openphone.ui.long_press_element",
  "openphone.ui.swipe",
  "openphone.ui.type_text",
  "openphone.input.press_key",
  "openphone.clipboard.set",
  "openphone.clipboard.paste",
  "openphone.share.text",
  "openphone.jobs.create",
  "openphone.jobs.stop",
];

function normalize(value: string | undefined): string {
  return (value ?? "").trim().toLowerCase();
}

function isOpenPhoneAndroidNode(ctx: OpenClawPluginNodeInvokePolicyContext): boolean {
  const platform = normalize(ctx.node?.platform);
  const family = normalize(ctx.node?.deviceFamily);
  return family === "openphone" && (platform === "" || platform.includes("android"));
}

async function invokeOpenPhoneNode(
  ctx: OpenClawPluginNodeInvokePolicyContext,
): Promise<OpenClawPluginNodeInvokePolicyResult> {
  if (!isOpenPhoneAndroidNode(ctx)) {
    return {
      ok: false,
      code: "unsupported_node",
      message: "The OpenPhone Android plugin only forwards commands to OpenPhone Android nodes.",
      details: {
        nodeId: ctx.nodeId,
        platform: ctx.node?.platform ?? "",
        deviceFamily: ctx.node?.deviceFamily ?? "",
        command: ctx.command,
      },
    };
  }
  return await ctx.invokeNode();
}

export default definePluginEntry({
  id: "openphone-android",
  name: "OpenPhone Android",
  description: "OpenPhone Android node command policy for OpenClaw.",
  register(api) {
    api.registerNodeInvokePolicy({
      commands: OPENPHONE_SAFE_READ_COMMANDS,
      defaultPlatforms: ["android"],
      handle: invokeOpenPhoneNode,
    });
    api.registerNodeInvokePolicy({
      commands: OPENPHONE_SCREEN_READ_COMMANDS,
      defaultPlatforms: ["android"],
      handle: invokeOpenPhoneNode,
    });
    api.registerNodeInvokePolicy({
      commands: OPENPHONE_ACTION_COMMANDS,
      dangerous: true,
      handle: invokeOpenPhoneNode,
    });
  },
});

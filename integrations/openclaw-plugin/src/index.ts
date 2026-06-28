import {
  definePluginEntry,
  type OpenClawPluginNodeInvokePolicyContext,
  type OpenClawPluginNodeInvokePolicyResult,
} from "openclaw/plugin-sdk/plugin-entry";

const OPENPHONE_SCREEN_READ_COMMANDS = [
  "openphone.apps.search",
  "device.apps",
  "openphone.screen.get",
  "canvas.snapshot",
  "openphone.screen.understand_local",
  "openphone.local.screen_understanding",
  "openphone.jobs.list",
];

const OPENPHONE_PRIVATE_READ_COMMANDS = [
  "openphone.device.status",
  "device.status",
  "device.info",
  "openphone.notifications.list",
  "notifications.list",
  "openphone.notifications.search",
  "notifications.search",
  "openphone.contacts.search",
  "contacts.search",
  "openphone.calendar.search",
  "calendar.events",
  "openphone.messages.search",
  "sms.search",
  "openphone.calls.search",
  "callLog.search",
  "openphone.memory.search",
  "openphone.watchers.list",
];

const OPENPHONE_ACTION_COMMANDS = [
  "openphone.notifications.open",
  "notifications.open",
  "openphone.calendar.add",
  "calendar.add",
  "openphone.calendar.update",
  "calendar.update",
  "openphone.calendar.delete",
  "calendar.delete",
  "openphone.messages.draft",
  "sms.draft",
  "openphone.messages.send",
  "sms.send",
  "openphone.calls.place",
  "calls.place",
  "openphone.memory.save",
  "openphone.watchers.create",
  "openphone.watchers.stop",
  "openphone.jobs.create",
  "openphone.jobs.stop",
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
];

function normalize(value: string | undefined): string {
  return (value ?? "").trim().toLowerCase();
}

function isOpenPhoneAndroidNode(ctx: OpenClawPluginNodeInvokePolicyContext): boolean {
  const platform = normalize(ctx.node?.platform);
  const family = normalize(ctx.node?.deviceFamily);
  const commands = ctx.node?.commands ?? [];
  const declaresOpenPhoneCommand = commands.some((command) =>
    normalize(command).startsWith("openphone."),
  );
  const androidLike =
    platform === "" ||
    platform.includes("android") ||
    family.includes("android") ||
    family.includes("openphone");
  const openPhoneLike =
    family.includes("openphone") ||
    normalize(ctx.node?.displayName).includes("openphone") ||
    declaresOpenPhoneCommand;
  return androidLike && openPhoneLike && declaresOpenPhoneCommand;
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
      commands: OPENPHONE_SCREEN_READ_COMMANDS,
      defaultPlatforms: ["android", "unknown"],
      handle: invokeOpenPhoneNode,
    });
    api.registerNodeInvokePolicy({
      commands: OPENPHONE_PRIVATE_READ_COMMANDS,
      dangerous: true,
      handle: invokeOpenPhoneNode,
    });
    api.registerNodeInvokePolicy({
      commands: OPENPHONE_ACTION_COMMANDS,
      dangerous: true,
      handle: invokeOpenPhoneNode,
    });
  },
});

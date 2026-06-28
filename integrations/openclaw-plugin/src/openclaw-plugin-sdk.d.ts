declare module "openclaw/plugin-sdk/plugin-entry" {
  export interface OpenClawPluginNode {
    platform?: string;
    deviceFamily?: string;
    displayName?: string;
    commands?: string[];
  }

  export interface OpenClawPluginNodeInvokePolicyContext {
    nodeId: string;
    node?: OpenClawPluginNode;
    command: string;
    invokeNode(): Promise<OpenClawPluginNodeInvokePolicyResult>;
  }

  export interface OpenClawPluginNodeInvokePolicyResult {
    ok: boolean;
    code?: string;
    message?: string;
    details?: Record<string, unknown>;
    [key: string]: unknown;
  }

  export interface OpenClawPluginNodeInvokePolicy {
    commands: string[];
    defaultPlatforms?: string[];
    dangerous?: boolean;
    handle(ctx: OpenClawPluginNodeInvokePolicyContext):
      | OpenClawPluginNodeInvokePolicyResult
      | Promise<OpenClawPluginNodeInvokePolicyResult>;
  }

  export interface OpenClawPluginApi {
    registerNodeInvokePolicy(policy: OpenClawPluginNodeInvokePolicy): void;
  }

  export interface OpenClawPluginEntry {
    id: string;
    name: string;
    description?: string;
    register(api: OpenClawPluginApi): void;
  }

  export function definePluginEntry(entry: OpenClawPluginEntry): OpenClawPluginEntry;
}

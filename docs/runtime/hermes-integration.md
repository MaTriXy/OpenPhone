# Hermes Integration

Hermes should not require another Android-side runtime rewrite. It should
implement the same Runtime Agent Protocol used by OpenClaw.

## Expected Shape

- Add a Hermes `RuntimeAdapter`.
- Reuse `RuntimeManager`, `RuntimeToolBridge`, phone sessions, confirmations,
  CLI, and MCP command manifests.
- Map Hermes session and tool-call events into `RuntimeToolRequest` and
  `RuntimeToolResult`.
- Keep Hermes-specific authentication, framing, and event parsing inside the
  adapter package.

## Voice

Short term, Hermes can use OpenPhone STT for voice input and Android TTS for
terminal text replies. Long term, Hermes can advertise realtime audio capability
and participate in the same runtime capability model as OpenAI Realtime, Gemini
Live, Qwen Omni, and OpenClaw voice.

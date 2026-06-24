# Schemas

This directory contains machine-readable JSON schemas for OpenPhone runtime
contracts.

They define the shapes that the assistant, framework patches, validators,
release tooling, and eval tooling must agree on:

- action requests and action results;
- model-visible tool registry entries;
- capability and app-policy configuration;
- screen-context payloads;
- audit events and audit evidence exports;
- trajectory events;
- background agent jobs and task reports;
- OTA feed metadata.

`scripts/check.sh` and the validation scripts use these schemas to catch drift
between model tools, framework actions, audit logs, eval traces, and release
artifacts.

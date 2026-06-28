# Contract Schemas

This directory contains machine-readable JSON Schema files for OpenPhone
contracts. These are not database schemas. They are payload contracts for data
that crosses process, tool, audit, eval, runtime, and release boundaries.

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

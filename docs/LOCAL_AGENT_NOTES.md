# Local Agent Notes

Use `docs/local-temp/` for local-only markdown that helps agents or humans work
through unfinished thoughts without publishing them.

Examples:

- scratch plans for a Codex run;
- temporary PR triage notes;
- private release checklists before they are sanitized;
- local eval observations that mention device-specific paths, screenshots, or
  trajectory details;
- drafts that may be wrong, contradictory, or not ready for public docs.

Rules:

- `docs/local-temp/` is ignored by git.
- Do not put source-of-truth project docs there.
- Do not rely on files there in scripts, CI, release notes, or public docs.
- Do not store secrets there; use ignored secret locations such as
  `.worktree/secrets/` instead.
- Before moving anything from `docs/local-temp/` into tracked docs, rewrite it
  as public documentation and remove private paths, device data, screenshots,
  trajectory details, tokens, and speculative claims.

Tracked docs should remain readable, current, and safe to publish. Local-temp
notes are disposable working memory.

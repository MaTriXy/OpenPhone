# OpenPhone Documentation

This is the public documentation map for OpenPhone. Historical implementation
logs and older plans live in [archive](archive/); start with the documents
below for current work.

## Start Here

- [Architecture](ARCHITECTURE.md) - system layers, OS services, agent runtime,
  and current implementation boundaries.
- [Specification](SPEC.md) - product thesis, non-goals, repository strategy,
  licensing model, and OS integration direction.
- [Build](BUILD.md) - Android repo sync, patch application, generic build, and
  Pixel 9a build commands.
- [Testing](TESTING.md) - repository checks, physical device smoke tests,
  assistant evals, and trajectory validation.
- [Capability Model](CAPABILITIES.md) - named capabilities, risk levels, and
  policy configuration.
- [Showcase](SHOWCASE.md) - current demo surface, screenshots/asset guidance,
  and what to show in public demos.
- [Roadmap](ROADMAP.md) - public preview roadmap and remaining hard problems.

## Device And Release Docs

- [Device Support](DEVICE_SUPPORT.md)
- [Pixel 9a Boot Chain](TEGU_BOOTCHAIN.md)
- [Google Mobile Services Notes](GMS.md)
- [Release Process](RELEASE_PROCESS.md)
- [0.0.1 Release Notes](releases/0.0.1.md)
- [Changelog](releases/CHANGELOG.md)

## Contracts

Machine-readable contracts live in [contracts](contracts/). These schemas are
used by repository checks and validators for action requests, model tools,
screen context, audit events, trajectories, OTA feeds, and agent eval reports.

## Licensing

- [Licensing Notes](LICENSING.md)
- [Legal Index](legal/README.md)
- [Commercial Licensing](legal/COMMERCIAL.md)
- [Contribution Terms](../.github/CONTRIBUTING.md)
- [Security Policy](../.github/SECURITY.md)

## Historical Notes

The archived docs are useful for project history and implementation evidence,
but they are not the public entry point:

- [Historical Master Plan](archive/THE_MASTER_PLAN.md)
- [Archived Active Plan](archive/PLAN.md)
- [Implementation Status Ledger](archive/IMPLEMENTATION_STATUS.md)
- [Bringup Log](archive/BRINGUP_LOG.md)
- [Framework Plan](archive/FRAMEWORK_PLAN.md)
- [Compose Plan](archive/COMPOSE_PLAN.md)

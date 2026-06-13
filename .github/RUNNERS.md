# Self-hosted runners for release + eval

The `release.yml` and `eval.yml` workflows need self-hosted runners — the
Android build is too big for GitHub-hosted runners, and the eval suite
needs a Pixel on USB. This file is the operator runbook for wiring them
up.

## openphone-build (release)

Runs `scripts/build.sh openphone_tegu`. Needs ~200GB disk and ~16GB RAM.

The EC2 instance at `ec2-18-189-1-174.us-east-2.compute.amazonaws.com`
already has:
- The full Android source tree at
  `/home/ubuntu/OpenPhone/.worktree/android`.
- `scripts/build.sh` working from the repo root.
- Java + soong toolchain installed.

To register it as a GitHub Actions runner:

```bash
ssh -i claudecode.pem ubuntu@ec2-18-189-1-174.us-east-2.compute.amazonaws.com

mkdir -p ~/actions-runner && cd ~/actions-runner
curl -o actions-runner-linux-x64.tar.gz -L \
  https://github.com/actions/runner/releases/latest/download/actions-runner-linux-x64-2.319.1.tar.gz
tar xzf actions-runner-linux-x64.tar.gz

# Get a fresh token from
#   https://github.com/<org>/<repo>/settings/actions/runners/new
./config.sh \
    --url https://github.com/<org>/<repo> \
    --token <token> \
    --labels self-hosted,openphone-build \
    --name openphone-build-ec2

# Install + start as a systemd service so it survives reboots:
sudo ./svc.sh install
sudo ./svc.sh start
```

After that, `release.yml` workflow_dispatch will route to this runner.

## openphone-device (eval)

Runs `scripts/run-eval-suite.sh` against a Pixel 9a on USB. The runner
host needs:
- `adb` on `PATH` and the Pixel authorized to it.
- The repo cloned with `.worktree/secrets/openai_api_key` populated
  (gitignored).
- The dev OpenAI API key already seeded into the Pixel:
  `adb shell settings put secure openphone_dev_openai_api_key sk-...`.
- The latest assistant APK on the device.

Right now this is most easily a Mac mini or Linux box on the same desk
as the phone. Register similarly to the build runner with the
`openphone-device` label.

## Why these are not GitHub-hosted

- **Build:** Android source tree is ~200GB checked out + ~80GB of build
  output. GitHub-hosted runners give ~14GB free disk; not enough.
- **Eval:** the suite needs a real Pixel on USB; GitHub does not have
  Android phones in its hosted fleet.

If a smaller "lint-only" CI is what you want, that already exists as
`ci.yml` (runs on `ubuntu-latest`, just executes `scripts/check.sh`).

#!/usr/bin/env bash
#
# One-time setup for the release pipeline.
#
#   1. Installs the release workflow where GitHub Actions expects it
#      (ci/release.yml -> .github/workflows/release.yml) and pushes it.
#   2. Triggers the workflow, which builds the signed APK, runs the tests,
#      regenerates the screenshots and attaches the APK to a GitHub Release.
#
# Requires: git (with push access to this repo) and, optionally, the GitHub
# CLI (`gh`) to trigger the run.
#
set -euo pipefail
cd "$(dirname "$0")/.."

command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 1; }

mkdir -p .github/workflows
cp ci/release.yml .github/workflows/release.yml

git add .github/workflows/release.yml
if git diff --cached --quiet; then
  echo "workflow already installed"
else
  git -c user.name="maui-ci" -c user.email="maui-ci@users.noreply.github.com" \
      commit -m "ci: enable the release workflow"
  git push
  echo "workflow installed and pushed"
fi

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  echo "triggering the release with the GitHub CLI..."
  gh workflow run release.yml -f version=v1.0
  echo "done — watch the run at:"
  echo "  gh run watch"
else
  echo
  echo "Trigger it with one of:"
  echo "  gh workflow run release.yml -f version=v1.0"
  echo "  …or from the Actions tab: https://github.com/YOUSSEFJEDIDI89/Maui/actions"
fi

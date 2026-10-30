#!/usr/bin/env bash
#
# One-time setup: install the release workflow where GitHub Actions expects it
# and push it. After this, the workflow can be triggered (manually with
# `gh workflow run release.yml -f version=v1.0`, or from an agent that has
# GitHub Actions access) and it will build the signed APK and publish a
# GitHub Release with the .apk attached.
#
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v git >/dev/null 2>&1; then
  echo "git is required" >&2
  exit 1
fi

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

echo
echo "Next: trigger the release with"
echo "  gh workflow run release.yml -f version=v1.0"
echo "or ask your agent to trigger it."

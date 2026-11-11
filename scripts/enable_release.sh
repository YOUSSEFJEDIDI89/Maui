#!/usr/bin/env bash
#
# One-time setup for the release pipeline.
#
#   1. Installs the release workflow where GitHub Actions expects it
#      (ci/release.yml -> .github/workflows/release.yml) and pushes it.
#   2. Creates the GitHub Release with the signed APK attached — either
#      directly through the GitHub CLI, or by letting CI build it.
#
# Requires: git (with push access to this repo). The GitHub CLI (`gh`) is
# optional but makes step 2 instant instead of waiting for a CI run.
#
set -euo pipefail
cd "$(dirname "$0")/.."

command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 1; }

VERSION="${1:-v1.0}"
VERSION="${VERSION#v}"

# ---------------------------------------------------------------- workflow
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

# ------------------------------------------------------------------ APK
APK="Maui-${VERSION}-release.apk"
if [ ! -f "$APK" ]; then
  if [ -d release ] && ls release/*.b64.* >/dev/null 2>&1; then
    echo "rebuilding the APK from the base64 mirror..."
    cat release/*.b64.* | base64 -d > "$APK"
  else
    APK="app/build/outputs/apk/release/app-release.apk"
    echo "using the freshly built APK at $APK"
  fi
fi
if [ ! -f "$APK" ]; then
  echo "No APK found. Build it first: ./gradlew :app:assembleRelease" >&2
  exit 1
fi
echo "APK: $APK ($(sha256sum "$APK" | cut -d' ' -f1))"

# ---------------------------------------------------------------- release
if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  if gh release view "v$VERSION" >/dev/null 2>&1; then
    echo "release v$VERSION already exists"
  else
    echo "creating the GitHub Release..."
    gh release create "v$VERSION" "$APK" \
      --title "Maui $VERSION" \
      --notes-file RELEASE_NOTES.md
    echo "release published"
  fi
  echo
  echo "watch the CI run with:  gh run watch"
else
  echo
  echo "Install the GitHub CLI (https://cli.github.com) and run 'gh auth login',"
  echo "then re-run this script to publish the release — or trigger CI instead:"
  echo "  gh workflow run release.yml -f version=$VERSION"
fi

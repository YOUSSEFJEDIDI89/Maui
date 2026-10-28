#!/usr/bin/env python3
"""Push the Maui source tree to GitHub through the connected GitHub App."""
import json
import os
import subprocess
import sys

ROOT = "/workspace/MauiSystem"
REPO = "Maui"
OWNER = "YOUSSEFJEDIDI89"
BRANCH = "main"
BATCH = 18
MAX_BYTES = 240_000

SKIP_DIRS = {"build", ".gradle", ".kotlin", ".git", "screenshots", "mipmap-mdpi",
             "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi",
             "mipmap-anydpi-v26"}
SKIP_FILES = {"local.properties", "gradle-wrapper.jar"}


def collect():
    files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel_dir = os.path.relpath(dirpath, ROOT)
        parts = rel_dir.split(os.sep)
        if any(p in SKIP_DIRS for p in parts):
            dirnames[:] = []
            continue
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn in SKIP_FILES:
                continue
            if fn.lower().endswith((".png", ".jpg", ".jpeg", ".webp", ".jar", ".keystore", ".jks", ".zip")):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, ROOT)
            try:
                content = open(full, "r", encoding="utf-8").read()
            except (UnicodeDecodeError, OSError):
                continue
            files.append((rel, content))
    return files


def push(batch, idx, total):
    args = {
        "owner": OWNER,
        "repo": REPO,
        "branch": BRANCH,
        "message": f"feat: publish Maui v1.0 — Android system layer (part {idx}/{total})",
        "files": [{"path": p, "content": c} for p, c in batch],
    }
    args_path = f"/tmp/push_args_{idx}.json"
    with open(args_path, "w", encoding="utf-8") as f:
        json.dump(args, f, ensure_ascii=False)
    cmd = ["mcode-tools", "connector", "call", "connector__github__push_files", "--args-file", args_path]
    res = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    ok = res.returncode == 0
    out = (res.stdout or res.stderr or "").strip()
    print(f"[{idx}/{total}] {'OK' if ok else 'FAIL'} ({len(batch)} files) -> {out[:220]}")
    return ok


def main():
    files = collect()
    print(f"collected {len(files)} text files")
    batches, cur, size = [], [], 0
    for path, content in files:
        if cur and (len(cur) >= BATCH or size + len(content) > MAX_BYTES):
            batches.append(cur)
            cur, size = [], 0
        cur.append((path, content))
        size += len(content)
    if cur:
        batches.append(cur)
    print(f"{len(batches)} batches")

    for i, b in enumerate(batches, 1):
        if not push(b, i, len(batches)):
            print("aborting after failure")
            sys.exit(1)
    print("ALL PUSHED")


if __name__ == "__main__":
    main()

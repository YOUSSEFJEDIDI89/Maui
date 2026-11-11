#!/usr/bin/env python3
"""Push the Maui source tree to GitHub, splitting batches that the connector rejects."""
import json
import os
import subprocess
import sys

OWNER, REPO, BRANCH = "YOUSSEFJEDIDI89", "Maui", "main"
ROOT = "/workspace/MauiSystem"
SKIP_DIRS = {"build", ".gradle", ".kotlin", ".git", "screenshots"}
SKIP_EXT = (".png", ".jpg", ".jpeg", ".webp", ".jar", ".keystore", ".jks", ".zip", ".apk")
SKIP_FILES = {"local.properties", "gradle-wrapper.jar"}

failed = []


def collect():
    files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel = os.path.relpath(dirpath, ROOT)
        parts = rel.split(os.sep)
        if any(p in SKIP_DIRS for p in parts):
            dirnames[:] = []
            continue
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn in SKIP_FILES or fn.lower().endswith(SKIP_EXT):
                continue
            full = os.path.join(dirpath, fn)
            try:
                content = open(full, "r", encoding="utf-8").read()
            except (UnicodeDecodeError, OSError):
                continue
            files.append((os.path.relpath(full, ROOT), content))
    return files


def push(batch, label):
    args = {
        "owner": OWNER, "repo": REPO, "branch": BRANCH,
        "message": label,
        "files": [{"path": p, "content": c} for p, c in batch],
    }
    open("/tmp/pp.json", "w", encoding="utf-8").write(json.dumps(args, ensure_ascii=False))
    r = subprocess.run(["mcode-tools", "connector", "call", "connector__github__push_files",
                        "--args-file", "/tmp/pp.json"], capture_output=True, text=True, timeout=900)
    return r.returncode == 0, (r.stdout or r.stderr or "")


def push_resilient(batch, label):
    ok, out = push(batch, label)
    if ok:
        print(f"  OK  {label} ({len(batch)} files)")
        return True
    if len(batch) == 1:
        print(f"  FAIL {batch[0][0]} -> {out[:120]}")
        failed.append(batch[0][0])
        return False
    mid = len(batch) // 2
    a = push_resilient(batch[:mid], label)
    b = push_resilient(batch[mid:], label)
    return a and b


def main():
    files = collect()
    print(f"collected {len(files)} text files")
    batches = []
    cur, size = [], 0
    for path, content in files:
        if len(content) > 300_000:
            if cur:
                batches.append(cur); cur, size = [], 0
            batches.append([(path, content)])
            continue
        if cur and (len(cur) >= 6 or size + len(content) > 500_000):
            batches.append(cur); cur, size = [], 0
        cur.append((path, content)); size += len(content)
    if cur:
        batches.append(cur)

    print(f"{len(batches)} batches")
    for i, b in enumerate(batches, 1):
        push_resilient(b, f"feat: Maui v1.1 — game launcher, game-space engine and UI polish ({i}/{len(batches)})")
    if failed:
        print("FAILED FILES:", failed)
        sys.exit(1)
    print("ALL PUSHED")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Finish the GitHub publish: remove probe leftovers, push the real files."""
import json
import os
import subprocess
import sys

OWNER, REPO, BRANCH = "YOUSSEFJEDIDI89", "Maui", "main"
ROOT = "/workspace/MauiSystem"

DELETE = [
    ".github/workflows",      # probe file where the real workflows dir must go
    ".github/notes.txt",
    "P1.yml", "P2.yml", "P3.yml", "P4.yml",
    "W25.yml", "W50.yml", "W75.yml", "W100.yml",
    "PUSH_TEST.txt", "docs/x.yml",
    "tools/cleanup_probes.py", "tools/push_to_github.py",
]


def call(tool, args, timeout=600):
    open("/tmp/g.json", "w", encoding="utf-8").write(json.dumps(args, ensure_ascii=False))
    r = subprocess.run(["mcode-tools", "connector", "call", tool, "--args-file", "/tmp/g.json"],
                       capture_output=True, text=True, timeout=timeout)
    return r.returncode == 0, (r.stdout or r.stderr)


def tree():
    ok, out = call("connector__github__get_repository_tree",
                   {"owner": OWNER, "repo": REPO, "branch": BRANCH, "recursive": True})
    if not ok:
        return {}
    d = json.loads(out)
    t = json.loads(d[0]["text"])
    return {e["path"]: e for e in t.get("tree", [])}


def push(files, message):
    args = {"owner": OWNER, "repo": REPO, "branch": BRANCH, "message": message,
            "files": [{"path": p, "content": c} for p, c in files]}
    ok, out = call("connector__github__push_files", args)
    return ok, out


def main():
    print("== deleting probe leftovers ==")
    t = tree()
    for path in DELETE:
        if path not in t:
            print(f"  {path}: not present")
            continue
        sha = t[path].get("sha")
        if not sha:
            print(f"  {path}: no sha")
            continue
        ok, out = call("connector__github__delete_file",
                       {"owner": OWNER, "repo": REPO, "path": path, "sha": sha,
                        "message": "chore: remove scaffolding file", "branch": BRANCH})
        print(f"  {path}: {'deleted' if ok else 'FAILED ' + out[:90]}")

    print("== pushing release assets ==")
    jobs = []
    jobs.append(("ci/release.yml", open(f"{ROOT}/ci/release.yml", encoding="utf-8").read()))
    jobs.append(("scripts/enable_release.sh", open(f"{ROOT}/scripts/enable_release.sh", encoding="utf-8").read()))
    jobs.append(("release/INSTALL.md", open(f"{ROOT}/release/INSTALL.md", encoding="utf-8").read()))
    for i in range(1, 13):
        p = f"{ROOT}/release/Maui-v1.0-release.apk.b64.{i:03d}"
        if os.path.exists(p):
            jobs.append((f"release/Maui-v1.0-release.apk.b64.{i:03d}", open(p, encoding="utf-8").read()))

    # small files together, big chunks one per request
    small = [j for j in jobs if len(j[1]) < 300_000]
    big = [j for j in jobs if len(j[1]) >= 300_000]
    ok, out = push(small, "docs: add release workflow, install helper and APK mirror instructions")
    print(f"  small batch ({len(small)} files): {'OK' if ok else 'FAIL ' + out[:140]}")
    for path, content in big:
        ok, out = push([(path, content)], f"release: add {os.path.basename(path)}")
        print(f"  {path} ({len(content)//1024} KB): {'OK' if ok else 'FAIL ' + out[:140]}")
        if not ok:
            sys.exit(1)

    print("== final tree ==")
    t = tree()
    paths = sorted(t.keys())
    print(f"  {len(paths)} entries")
    for p in paths:
        if p.count("/") <= 1:
            print("   ", p)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Remove the probe/test files created while diagnosing the connector."""
import json
import subprocess

OWNER = "YOUSSEFJEDIDI89"
REPO = "Maui"
BRANCH = "main"
JUNK = ["TEST.txt", "TEST2.txt", "TEST3.txt", "OTHER.md", "B0.txt", "B1.txt", "B2.txt",
        "B3.txt", "P4000.txt", "P6000.txt", "P7000.txt", "P8000.txt", "P9000.txt",
        "P9249.txt", "T12000.txt", "T20000.txt", "T40000.txt"]


def call(tool, args):
    path = "/tmp/cleanup_args.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(args, f, ensure_ascii=False)
    r = subprocess.run(["mcode-tools", "connector", "call", tool, "--args-file", path],
                       capture_output=True, text=True, timeout=300)
    return r.returncode == 0, (r.stdout or r.stderr)


for name in JUNK:
    ok, out = call("connector__github__get_file_contents", {"owner": OWNER, "repo": REPO, "path": name})
    sha = None
    if ok:
        try:
            d = json.loads(out)
            for item in d:
                if item.get("type") == "resource":
                    sha = item["resource"].get("uri", "").split("/contents/")[0].split("/sha/")[-1]
                    if not sha:
                        sha = None
        except Exception:
            sha = None
    # fall back: list tree to get blob sha
    if not sha:
        ok2, out2 = call("connector__github__get_repository_tree", {"owner": OWNER, "repo": REPO, "branch": BRANCH, "recursive": "true"})
        if ok2:
            try:
                d = json.loads(out2)
                for item in d:
                    t = json.loads(item["text"])
                    for e in t.get("tree", []):
                        if e.get("path") == name:
                            sha = e.get("sha")
            except Exception:
                pass
    if not sha:
        print(f"{name}: sha not found, skipping")
        continue
    ok3, out3 = call("connector__github__delete_file",
                     {"owner": OWNER, "repo": REPO, "path": name, "sha": sha, "message": "chore: remove probe file", "branch": BRANCH})
    print(f"{name}: {'deleted' if ok3 else 'FAILED ' + out3[:80]}")

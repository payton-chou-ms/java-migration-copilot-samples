#!/usr/bin/env bash
# repo-hygiene-audit: cross-repo correlator
# Usage: cross_repo.sh <repo1> <repo2> ...
# Output: JSON with duplicate basenames, shared-code candidates

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: cross_repo.sh <repo1> <repo2> [...]" >&2
  exit 2
fi

REPOS=("$@")
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# collect every tracked file from each repo into a tagged list
for repo in "${REPOS[@]}"; do
  repo_abs="$(cd "$repo" && pwd)"
  name=$(basename "$repo_abs")
  if (cd "$repo_abs" && git rev-parse --git-dir >/dev/null 2>&1); then
    (cd "$repo_abs" && git ls-files) | awk -v r="$name" -v p="$repo_abs" '{print r"|"p"|"$0}'
  else
    (cd "$repo_abs" && find . -type f -not -path './.git/*' | sed 's|^\./||') | awk -v r="$name" -v p="$repo_abs" '{print r"|"p"|"$0}'
  fi
done > "$TMP/all_files.txt"

python3 << PYEOF
import json, os, hashlib, re
from collections import defaultdict

entries = []
with open("$TMP/all_files.txt") as f:
    for line in f:
        line = line.rstrip("\n")
        if not line: continue
        parts = line.split("|", 2)
        if len(parts) != 3: continue
        entries.append({"repo": parts[0], "repo_path": parts[1], "rel": parts[2]})

# --- duplicate basenames (≥2 repos) ---
by_base = defaultdict(list)
for e in entries:
    base = os.path.basename(e["rel"])
    if base in {"__init__.py", ".gitkeep", ".gitignore", "README.md", "pyproject.toml",
                "requirements.txt", "Dockerfile", "ruff.toml", "pyrightconfig.json",
                "pytest.ini", "uv.lock", "package.json", ".python-version"}:
        continue
    by_base[base].append(e)

dup_basenames = []
for base, lst in by_base.items():
    repos = sorted({x["repo"] for x in lst})
    if len(repos) >= 2:
        dup_basenames.append({
            "basename": base,
            "repos": repos,
            "occurrences": [{"repo": x["repo"], "rel": x["rel"]} for x in lst],
        })
dup_basenames.sort(key=lambda x: -len(x["repos"]))

# --- content-identical files (md5 across repos) ---
def md5_of(path, limit=512*1024):
    try:
        with open(path, "rb") as f:
            data = f.read(limit)
        return hashlib.md5(data).hexdigest(), len(data)
    except OSError:
        return None, 0

by_hash = defaultdict(list)
for e in entries:
    full = os.path.join(e["repo_path"], e["rel"])
    if not os.path.isfile(full): continue
    try:
        size = os.path.getsize(full)
    except OSError:
        continue
    if size == 0 or size > 2*1024*1024: continue  # skip empty and huge
    h, _ = md5_of(full)
    if not h: continue
    by_hash[h].append({"repo": e["repo"], "rel": e["rel"], "bytes": size})

identical = []
for h, lst in by_hash.items():
    repos = {x["repo"] for x in lst}
    if len(repos) >= 2:
        identical.append({"hash": h, "bytes": lst[0]["bytes"], "occurrences": lst})
identical.sort(key=lambda x: -x["bytes"])

# --- tcoe.* migration: local copies of shared packages ---
tcoe_smells = []
smell_patterns = {
    "shared_db/": "should use tcoe.db (from tcoe-infra)",
    "backend/auth/auth.py": "should use tcoe.auth.verify_token",
    "backend/auth/usage_log.py": "should use tcoe.auth.usage_log",
    "backend/usage/": "should use tcoe.usage",
    "backend/storage/": "should use tcoe.storage",
    "tcoe_auth/": "local copy; should use tcoe.auth from infra package",
}
for e in entries:
    if e["repo"] == "poc-ctbc-tcoe-infra": continue
    for pat, hint in smell_patterns.items():
        if e["rel"].startswith(pat) or e["rel"] == pat.rstrip("/"):
            tcoe_smells.append({"repo": e["repo"], "path": e["rel"], "suggestion": hint})
            break

# --- repos audited ---
repos_meta = []
seen = set()
for e in entries:
    if e["repo"] in seen: continue
    seen.add(e["repo"])
    repos_meta.append({"name": e["repo"], "path": e["repo_path"]})

print(json.dumps({
    "schema": "repo-hygiene-audit-cross/v1",
    "repos": repos_meta,
    "duplicate_basenames": dup_basenames[:100],
    "content_identical": identical[:50],
    "tcoe_migration_candidates": tcoe_smells[:100],
}, indent=2, ensure_ascii=False))
PYEOF

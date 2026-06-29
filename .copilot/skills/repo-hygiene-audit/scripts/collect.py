#!/usr/bin/env python3
"""
repo-hygiene-audit: single-repo fact collector (pure Python — fast, no shell pitfalls).

Usage:
  collect.py <repo_path> [--age-days 180] [--output audit.json]
"""
import argparse, json, os, re, subprocess, sys, time, datetime, hashlib
from pathlib import Path
from collections import defaultdict

EXCLUDE_DIRS = {".git", "node_modules", ".venv", "venv", "__pycache__",
                ".pytest_cache", ".mypy_cache", ".ruff_cache", "dist", "build",
                ".next", ".turbo", ".cache", "site-packages"}

SUS_FILE_PATTERNS = re.compile(
    r"(\.(log|tmp|bak|swp|orig|pyc)$|^\._|/\._|\.DS_Store$|^tmp.*\.html$|nohup\.out$|~$)"
)
SUS_DIR_NAMES = {"__pycache__", "node_modules", ".venv", "venv", "dist", "build",
                 ".pytest_cache", ".mypy_cache", ".ruff_cache"}

NAMING_OLD_RE = re.compile(r"(_old|_v1|_deprecated|\.bak$|/legacy/|/archive/|_backup/|-copy\.)")
DEP_MARKER_RE = re.compile(r"(TODO:?\s*remove|DEPRECATED|@deprecated|#\s*unused|\bXXX\b)", re.I)
CODE_EXTS = {".py", ".ts", ".tsx", ".js", ".jsx", ".go", ".rs", ".java"}
DOC_EXTS = {".md", ".rst"}
BANNED_DOC_TERMS = re.compile(r"\b(poetry|pipenv)\b|^pip install|python 3\.(8|9|10)\b", re.I | re.M)
MD_LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")


def run(cmd, cwd=None, check=False):
    try:
        return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                              check=check, timeout=30).stdout
    except (subprocess.TimeoutExpired, subprocess.CalledProcessError):
        return ""


def is_git_repo(repo: Path) -> bool:
    return (repo / ".git").exists() or bool(run(["git", "rev-parse", "--git-dir"], cwd=repo).strip())


def tracked_files(repo: Path) -> list[str]:
    out = run(["git", "ls-files"], cwd=repo)
    return [l for l in out.splitlines() if l]


def walk_all(repo: Path) -> list[str]:
    out = []
    for root, dirs, files in os.walk(repo):
        rel_root = os.path.relpath(root, repo)
        # prune excluded dirs
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
        for f in files:
            full = Path(root) / f
            rel = os.path.relpath(full, repo)
            out.append(rel.replace(os.sep, "/"))
    return out


def size_block(repo: Path) -> dict:
    top_dirs = []
    for entry in repo.iterdir():
        if entry.name in EXCLUDE_DIRS - {".git"}:  # include .venv etc as "this is bloat"
            pass
        try:
            if entry.is_dir():
                total = 0
                for r, ds, fs in os.walk(entry):
                    ds[:] = [d for d in ds if d != ".git"]
                    for f in fs:
                        try: total += os.path.getsize(os.path.join(r, f))
                        except OSError: pass
                top_dirs.append({"path": entry.name + "/", "bytes": total})
            elif entry.is_file():
                top_dirs.append({"path": entry.name, "bytes": entry.stat().st_size})
        except OSError:
            pass
    top_dirs.sort(key=lambda x: -x["bytes"])

    # top files (only from non-excluded paths)
    all_files = []
    for r, ds, fs in os.walk(repo):
        ds[:] = [d for d in ds if d not in EXCLUDE_DIRS]
        for f in fs:
            full = os.path.join(r, f)
            try:
                all_files.append((os.path.getsize(full), os.path.relpath(full, repo).replace(os.sep, "/")))
            except OSError:
                pass
    all_files.sort(reverse=True)
    top_files = [{"path": p, "bytes": s} for s, p in all_files[:20]]
    total = sum(s for s, _ in all_files)
    return {"total_bytes": total, "top_dirs": top_dirs[:10], "top_files": top_files}


def git_block(repo: Path) -> dict:
    untracked = len([l for l in run(["git", "ls-files", "--others", "--exclude-standard"], cwd=repo).splitlines() if l])
    modified = len([l for l in run(["git", "diff", "--name-only"], cwd=repo).splitlines() if l])
    staged = len([l for l in run(["git", "diff", "--cached", "--name-only"], cwd=repo).splitlines() if l])
    stash = len([l for l in run(["git", "stash", "list"], cwd=repo).splitlines() if l])
    branch = run(["git", "branch", "--show-current"], cwd=repo).strip()

    ahead_behind = run(["git", "rev-list", "--left-right", "--count", "@{u}...HEAD"], cwd=repo).strip()
    behind, ahead = 0, 0
    if ahead_behind:
        parts = ahead_behind.split()
        if len(parts) == 2:
            behind, ahead = int(parts[0]), int(parts[1])

    # large blobs in history (>1MB)
    large_blobs = []
    revlist = run(["git", "rev-list", "--objects", "--all"], cwd=repo)
    if revlist:
        # batch-check
        proc = subprocess.run(
            ["git", "cat-file", "--batch-check=%(objecttype) %(objectname) %(objectsize) %(rest)"],
            input=revlist, capture_output=True, text=True, cwd=repo, timeout=60
        )
        for line in proc.stdout.splitlines():
            parts = line.split(" ", 3)
            if len(parts) < 3 or parts[0] != "blob": continue
            try: size = int(parts[2])
            except ValueError: continue
            if size > 1_048_576:
                large_blobs.append({"sha": parts[1], "bytes": size, "path": parts[3] if len(parts) > 3 else ""})
        large_blobs.sort(key=lambda x: -x["bytes"])
        large_blobs = large_blobs[:20]

    # orphan branches (no upstream)
    refs = run(["git", "for-each-ref", "--format=%(refname:short)|%(upstream)", "refs/heads"], cwd=repo)
    orphan = [l.split("|")[0] for l in refs.splitlines() if l and l.endswith("|")]

    return {
        "untracked": untracked, "modified": modified, "staged": staged,
        "stash_count": stash, "current_branch": branch,
        "ahead": ahead, "behind": behind,
        "large_blobs": large_blobs, "orphan_branches": orphan,
    }


def suspicious_files(repo: Path) -> list[dict]:
    out = []
    for r, ds, fs in os.walk(repo):
        # don't descend into .git; do report SUS_DIR_NAMES as items but don't recurse
        ds[:] = [d for d in ds if d != ".git"]
        prune = []
        for d in list(ds):
            if d in SUS_DIR_NAMES:
                full = os.path.join(r, d)
                try:
                    size = sum(os.path.getsize(os.path.join(rr, ff))
                               for rr, _, ffs in os.walk(full) for ff in ffs
                               if os.path.isfile(os.path.join(rr, ff)))
                except OSError:
                    size = 0
                out.append({"path": os.path.relpath(full, repo).replace(os.sep, "/"),
                            "bytes": size, "is_dir": True})
                prune.append(d)
        for d in prune:
            ds.remove(d)
        for f in fs:
            rel = os.path.relpath(os.path.join(r, f), repo).replace(os.sep, "/")
            if SUS_FILE_PATTERNS.search(rel) or SUS_FILE_PATTERNS.search(f):
                try: sz = os.path.getsize(os.path.join(r, f))
                except OSError: sz = 0
                out.append({"path": rel, "bytes": sz, "is_dir": False})
    out.sort(key=lambda x: -x["bytes"])
    return out[:100]


def old_code_block(repo: Path, files: list[str], age_days: int) -> dict:
    cutoff = int(time.time()) - age_days * 86400
    stale = []
    for f in files:
        full = repo / f
        if not full.is_file(): continue
        ts_str = run(["git", "log", "-1", "--format=%ct", "--", f], cwd=repo).strip()
        if ts_str and ts_str.isdigit():
            ts = int(ts_str)
            if 0 < ts < cutoff:
                stale.append({"path": f, "last_commit": datetime.datetime.fromtimestamp(ts, datetime.timezone.utc).isoformat().replace("+00:00", "Z"), "ts": ts})
    stale.sort(key=lambda x: x["ts"])
    for s in stale: s.pop("ts", None)

    naming = [{"path": f} for f in files if NAMING_OLD_RE.search(f)][:100]

    # parallel impls: foo + foo_v2, dir-level
    dir_set = set()
    for f in files:
        parts = f.split("/")
        for i in range(1, len(parts)):
            dir_set.add("/".join(parts[:i]))
    parallel = []
    for d in dir_set:
        m = re.match(r"^(.+)_v(\d+)$", d)
        if m and m.group(1) in dir_set:
            parallel.append({"newer": d, "older_candidate": m.group(1)})
    # file-level too
    file_set = set(files)
    for f in files:
        m = re.match(r"^(.+)_v(\d+)(\.\w+)$", f)
        if m:
            older = m.group(1) + m.group(3)
            if older in file_set:
                parallel.append({"newer": f, "older_candidate": older})

    # deprecated markers
    markers = []
    for f in files:
        if Path(f).suffix not in CODE_EXTS: continue
        try:
            with open(repo / f, encoding="utf-8", errors="ignore") as fh:
                for ln, line in enumerate(fh, 1):
                    if DEP_MARKER_RE.search(line):
                        markers.append({"path": f, "line": ln, "snippet": line.strip()[:120]})
                        if len(markers) >= 100: break
        except OSError:
            pass
        if len(markers) >= 100: break

    # gitignored but tracked
    gi_tracked = [{"path": l} for l in run(["git", "ls-files", "-i", "--exclude-standard"], cwd=repo).splitlines() if l][:50]

    return {
        "stale_by_age": stale[:100],
        "naming_signals": naming,
        "parallel_implementations": parallel[:50],
        "deprecated_markers": markers,
        "gitignored_but_tracked": gi_tracked,
    }


def old_docs_block(repo: Path, files: list[str]) -> dict:
    docs = [f for f in files if Path(f).suffix.lower() in DOC_EXTS]

    banned = []
    for f in docs:
        try:
            txt = (repo / f).read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if BANNED_DOC_TERMS.search(txt):
            banned.append({"path": f})

    broken = []
    for f in docs:
        try:
            txt = (repo / f).read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for ln, line in enumerate(txt.splitlines(), 1):
            for m in MD_LINK_RE.finditer(line):
                target = m.group(2).split("#")[0].split("?")[0].strip()
                if not target or target.startswith(("http://", "https://", "mailto:", "#")):
                    continue
                if target.startswith("/"):
                    resolved = repo / target.lstrip("/")
                else:
                    resolved = (repo / f).parent / target
                if not resolved.exists():
                    broken.append({"doc": f, "line": ln, "target": target})
                    if len(broken) >= 100: break
            if len(broken) >= 100: break
        if len(broken) >= 100: break

    readme_like = []
    for f in files:
        name = os.path.basename(f).lower()
        if re.match(r"^(readme|quickstart|getting[-_]?started)", name):
            try: sz = (repo / f).stat().st_size
            except OSError: sz = 0
            readme_like.append({"path": f, "bytes": sz})

    return {
        "mentions_banned_tools": banned[:50],
        "broken_links": broken,
        "multiple_readmes": readme_like[:50],
    }


def dep_block(repo: Path) -> dict:
    keys = {
        "has_pyproject": "pyproject.toml",
        "has_requirements_txt": "requirements.txt",
        "has_uv_lock": "uv.lock",
        "has_poetry_lock": "poetry.lock",
        "has_pipfile": "Pipfile",
        "has_package_json": "package.json",
        "has_package_lock": "package-lock.json",
        "has_pnpm_lock": "pnpm-lock.yaml",
        "has_yarn_lock": "yarn.lock",
    }
    return {k: (repo / v).is_file() for k, v in keys.items()}


def doc_health(repo: Path) -> dict:
    return {
        "has_readme": any((repo / n).is_file() for n in ["README.md", "README.rst", "readme.md", "README"]),
        "has_changelog": any((repo / n).is_file() for n in ["CHANGELOG.md", "CHANGES.md", "HISTORY.md"]),
        "has_gitignore": (repo / ".gitignore").is_file(),
        "has_license": any((repo / n).is_file() for n in ["LICENSE", "LICENSE.md", "LICENSE.txt"]),
        "has_contributing": (repo / "CONTRIBUTING.md").is_file(),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("repo_path")
    ap.add_argument("--age-days", type=int, default=180)
    ap.add_argument("--output", "-o")
    args = ap.parse_args()

    repo = Path(args.repo_path).resolve()
    if not repo.is_dir():
        print(f"not a directory: {repo}", file=sys.stderr); sys.exit(2)

    is_git = is_git_repo(repo)
    files = tracked_files(repo) if is_git else walk_all(repo)

    print(f"audit: {repo} (git={is_git}, files={len(files)})", file=sys.stderr)

    audit = {
        "schema": "repo-hygiene-audit/v1",
        "timestamp": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "repo": {"path": str(repo), "name": repo.name, "is_git": is_git},
        "config": {"age_days": args.age_days, "file_count": len(files)},
        "size": size_block(repo),
        "git": git_block(repo) if is_git else None,
        "suspicious_files": suspicious_files(repo),
        "old_code": old_code_block(repo, files, args.age_days) if is_git else {
            "stale_by_age": [], "naming_signals": [], "parallel_implementations": [],
            "deprecated_markers": [], "gitignored_but_tracked": [],
        },
        "old_docs": old_docs_block(repo, files),
        "dependencies": dep_block(repo),
        "doc_health": doc_health(repo),
    }

    out = json.dumps(audit, indent=2, ensure_ascii=False)
    if args.output:
        Path(args.output).write_text(out)
        print(f"wrote: {args.output}", file=sys.stderr)
    else:
        print(out)


if __name__ == "__main__":
    main()

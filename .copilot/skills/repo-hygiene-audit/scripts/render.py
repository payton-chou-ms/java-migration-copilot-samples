#!/usr/bin/env python3
"""
repo-hygiene-audit: 從收集的 JSON 產生 Markdown + HTML 報告（中文輸出）。

用法:
  render.py <audit.json> [--cross cross.json] [--interpretation interp.json] -o <out_dir>

若未提供 --interpretation，使用內建啟發式規則分類。
輸出:
  <out_dir>/report.md
  <out_dir>/report.html
  <out_dir>/findings.json
"""
import argparse, json, os, re, html
from pathlib import Path

# ----- 中文對照表 -----

CAT_ZH = {
    "delete": "刪除",
    "archive": "歸檔",
    "migrate": "遷移/更新",
    "review": "人工審查",
    "keep": "保留",
}
SEV_ZH = {
    "blocker": "阻斷",
    "high": "高",
    "medium": "中",
    "low": "低",
    "info": "資訊",
}
REV_ZH = {
    "safe-auto": "可自動（安全）",
    "safe-manual": "手動（安全）",
    "needs-review": "需審查",
    "dangerous": "危險（不可逆）",
}

# 哪些動作開頭視為「可直接執行」的指令（其餘視為手動步驟，腳本中註解）
EXEC_RE = re.compile(r"^(rm|git|uv|mv|find|tar|echo|cp|mkdir)\b")

# 各階段的中繼資料：phase -> (標題, 建議, 影響, 風險)
PHASE_META = {
    0: ("備份（必做）",
        "執行任何清理前一定要先做：打包整個 repo + git stash + 記錄目前 commit。",
        "之後任何階段出錯都能完整還原。", "無。"),
    1: ("安全自動清理",
        "刪除快取、建置產出物、macOS 中繼資料等可重新產生的檔案。建議直接採用。",
        "倉庫體積縮小、git status 變乾淨。", "極低（這些檔案都能重新產生）。"),
    2: ("Git 衛生",
        "處理未追蹤檔、stash、大型 blob；未追蹤檔該加入 .gitignore 或 stash。",
        "git 狀態清爽，倉庫不再夾帶雜物。",
        "低；唯獨大型 blob 的歷史改寫不可逆，需獨立處理並團隊協調。"),
    3: ("重複 / 暫存 / 草稿檔",
        "tmp*.html、*.bak、重複內容等需人工瞄一眼確認非進行中工作。",
        "減少混淆，主目錄更乾淨。", "中（可能是還沒收尾的草稿，先確認）。"),
    4: ("死碼 / 舊程式碼",
        "命名標示棄用、平行新舊版本、被 .gitignore 卻仍追蹤的程式碼；確認無引用後歸檔。",
        "移除平行實作與死碼，降低維護成本與混淆。",
        "中（兩版可能仍被引用，歸檔可逆、刪除前務必確認）。"),
    5: ("過期文件",
        "失效連結、提到禁用工具（poetry/pipenv/pip）、多份 README 等文件問題。",
        "文件與實際工具鏈一致，新人不被誤導。", "低（純文件修改，可逆）。"),
    6: ("依賴與設定",
        "對齊 pyproject.toml ↔ requirements.txt ↔ uv.lock；補缺 lockfile、移除被禁用的工具設定。",
        "依賴定義單一來源、建置可重現。",
        "中（部署腳本可能仍依賴 requirements.txt，移除前先確認）。"),
    7: ("跨 repo 對齊",
        "把本地重複、且 tcoe-infra 已有的程式碼改用 tcoe.* 共用套件。",
        "消除跨 repo 重複，集中維護於單一真實來源。",
        "中（需確認 API 相容並更新所有 import）。"),
    8: ("驗證",
        "每完成一個階段就跑 lint + 測試，確保沒有改壞東西。",
        "確保清理未破壞功能。", "無。"),
}


def is_exec(action: str) -> bool:
    return bool(EXEC_RE.match(action.strip()))


def with_meta(f: dict) -> dict:
    """為每個 finding 補上 建議/影響/風險/推薦決策（若分類器未提供）。"""
    cat = f.get("category", "review")
    rev = f.get("reversibility", "needs-review")
    f.setdefault("recommendation", {
        "delete": "確認無引用後刪除",
        "archive": "移到 docs/archive/ 並保留歷史",
        "migrate": "依共用套件 / uv 規範更新",
        "review": "人工確認用途再決定",
        "keep": "維持現狀",
    }.get(cat, "人工確認"))
    f.setdefault("impact", {
        "delete": "減少倉庫體積與雜訊",
        "archive": "主目錄更乾淨，歷史仍可查",
        "migrate": "消除重複程式碼，對齊單一真實來源",
        "review": "釐清現況，降低維護負擔",
        "keep": "無",
    }.get(cat, "改善倉庫整潔度"))
    f.setdefault("risk", {
        "safe-auto": "極低（產出物可重建）",
        "safe-manual": "低（建議先 dry-run）",
        "needs-review": "中（可能仍有引用，需先確認）",
        "dangerous": "高（會改寫 git 歷史，不可逆）",
    }.get(rev, "中"))
    # 推薦決策：盡量幫使用者先做決定，只有「不可逆」預設略過。
    if "rec" not in f:
        if rev == "dangerous":
            f["rec"] = "skip"
            f["rec_reason"] = "不可逆，預設略過；需團隊協調後再手動處理"
        elif rev == "safe-auto":
            f["rec"] = "adopt"
            f["rec_reason"] = "可重建、零風險，建議直接採用"
        elif rev == "safe-manual":
            f["rec"] = "adopt"
            f["rec_reason"] = "低風險，建議採用（已預設 dry-run）"
        elif cat in ("migrate", "archive") or f.get("doc_update"):
            f["rec"] = "adopt"
            f["rec_reason"] = "建議採用；歸檔/更新可逆，採用前先快速確認引用"
        else:
            f["rec"] = "adopt"
            f["rec_reason"] = "建議採用，但採用前請先確認用途"
    return f


def assign_phase(f: dict) -> int:
    """把 finding 歸到某個 Phase（1-7）。"""
    fid = str(f.get("id", ""))
    if fid.startswith("X"):
        return 7
    cat = f.get("category", "review")
    rev = f.get("reversibility", "needs-review")
    path = f.get("path", "")
    ev = "；".join(f.get("evidence", []))
    if any(k in path for k in ("requirements.txt", "uv.lock", "poetry.lock")) or \
            os.path.basename(path) in ("README.md", ".gitignore"):
        return 6
    if cat == "delete" and rev == "safe-auto":
        return 1
    if any(k in ev for k in ("未追蹤", "stash", "blob")) or path in ("（工作目錄）", "（git stash）"):
        return 2
    if f.get("doc_update"):
        return 5
    if any(k in ev for k in ("命名顯示已棄用", "平行", "較新版本", "gitignore", "程式碼標記", "未變更")):
        return 4
    if cat == "delete":
        return 3
    if cat in ("archive", "review", "migrate"):
        return 3
    return 3


# ----- 啟發式分類器（無 LLM interpretation 時使用）-----

def classify(audit: dict) -> list[dict]:
    findings = []
    fid = 0

    def add(**kw):
        nonlocal fid
        fid += 1
        kw.setdefault("id", f"F{fid:03d}")
        findings.append(with_meta(kw))

    # 可疑檔案
    for s in audit.get("suspicious_files", []):
        p = s["path"]
        size = s["bytes"]
        if any(p.endswith(x) or f"/{x}" in p for x in ("__pycache__", ".pytest_cache",
                ".mypy_cache", ".ruff_cache", "node_modules", ".venv", "dist", "build")):
            add(path=p, category="delete", severity="low", reversibility="safe-auto",
                evidence=["建置/快取產出物", f"{size} bytes"],
                action=f"rm -rf {p}", user_decision=False)
        elif p.endswith(".DS_Store") or "/._" in p or p.startswith("._"):
            add(path=p, category="delete", severity="info", reversibility="safe-auto",
                evidence=["macOS 中繼資料"], action=f"rm '{p}'", user_decision=False)
        elif any(p.endswith(x) for x in (".log", ".tmp", ".bak", "~", ".swp", ".orig")):
            add(path=p, category="delete", severity="low", reversibility="safe-manual",
                evidence=["暫存/備份檔"],
                action=f"rm '{p}'", user_decision=True)
        elif "tmp" in os.path.basename(p).lower() and p.endswith(".html"):
            add(path=p, category="review", severity="low", reversibility="safe-manual",
                evidence=["tmp 開頭的 html，疑似草稿"], action=f"rm '{p}'  # 確認為草稿後執行",
                user_decision=True)

    # 舊程式碼
    oc = audit.get("old_code", {})
    for f in oc.get("naming_signals", [])[:30]:
        p = f["path"]
        if "/archive/" in p or p.startswith("archive/") or "/legacy/" in p:
            continue
        add(path=p, category="archive", severity="medium",
            reversibility="needs-review",
            evidence=["命名顯示已棄用 (_old/_v1/_deprecated/.bak 等)"],
            action=f"git mv {p} docs/archive/$(date +%Y%m%d)/",
            user_decision=True)
    for f in oc.get("parallel_implementations", []):
        add(path=f["older_candidate"], category="review", severity="high",
            reversibility="needs-review",
            evidence=[f"存在較新版本: {f['newer']}（可能舊版已無人使用）"],
            action=f"grep -rn {os.path.basename(f['older_candidate'])} --include='*.py' .  # 先查引用",
            recommendation="確認新版已完全取代後，歸檔或刪除舊版",
            impact="移除平行實作，降低混淆與維護成本",
            risk="中（兩版可能仍同時被引用，需逐一確認）",
            user_decision=True)
    for f in oc.get("gitignored_but_tracked", []):
        add(path=f["path"], category="review", severity="medium",
            reversibility="needs-review",
            evidence=["已被 .gitignore 列入卻仍被追蹤（通常代表想移除未移除）"],
            action=f"git rm --cached '{f['path']}'",
            user_decision=True)
    for f in oc.get("deprecated_markers", [])[:20]:
        add(path=f"{f['path']}:{f['line']}", category="review", severity="low",
            reversibility="needs-review",
            evidence=[f"程式碼標記: {f['snippet'][:80]}"],
            action=f"$EDITOR '{f['path']}'  # 人工審查標記處",
            user_decision=True)
    for f in oc.get("stale_by_age", [])[:20]:
        add(path=f["path"], category="review", severity="info",
            reversibility="needs-review",
            evidence=[f"自 {f['last_commit']} 起未變更"],
            action=f"git log -1 --stat -- '{f['path']}'  # 確認是否仍在使用",
            user_decision=True)

    # 舊文件
    od = audit.get("old_docs", {})
    for f in od.get("mentions_banned_tools", []):
        add(path=f["path"], category="migrate", severity="medium",
            reversibility="needs-review",
            evidence=["提到 poetry / pipenv / 裸 pip install（本專案規範用 uv）"],
            action=f"$EDITOR '{f['path']}'  # 安裝/執行指令改用 uv sync / uv run",
            recommendation="把安裝/執行指令改成 uv sync / uv run",
            impact="文件與實際工具鏈一致，避免誤導新進人員",
            risk="低（純文件修改）",
            doc_update=True,
            user_decision=True)
    for f in od.get("broken_links", [])[:30]:
        add(path=f"{f['doc']}:{f['line']}", category="review", severity="low",
            reversibility="safe-manual",
            evidence=[f"連結失效 → {f['target']}"],
            action=f"$EDITOR '{f['doc']}'  # 修正或移除失效連結",
            recommendation="更新指向正確路徑，或刪除已失效連結",
            impact="文件可正常導覽",
            risk="低（純文件修改）",
            doc_update=True,
            user_decision=True)
    readmes = od.get("multiple_readmes", [])
    if len(readmes) > 1:
        add(path="（多份 README）", category="review", severity="info",
            reversibility="needs-review",
            evidence=[f"共 {len(readmes)} 份 README 類文件: " +
                      ", ".join(r["path"] for r in readmes[:5])],
            action="人工整併或交叉連結多份 README",
            doc_update=True,
            user_decision=True)

    # git 衛生
    git = audit.get("git") or {}
    if git.get("untracked", 0) > 20:
        add(path="（工作目錄）", category="review", severity="medium",
            reversibility="safe-manual",
            evidence=[f"{git['untracked']} 個未追蹤檔案"],
            action="git status -u  # 檢視後 stash 或加入 .gitignore",
            user_decision=True)
    if git.get("stash_count", 0) > 0:
        add(path="（git stash）", category="review", severity="low",
            reversibility="safe-manual",
            evidence=[f"{git['stash_count']} 筆 stash 暫存"],
            action="git stash list  # 逐筆 drop 或 apply",
            user_decision=True)
    for lb in git.get("large_blobs", [])[:5]:
        add(path=lb.get("path", lb.get("sha", "?")),
            category="review", severity="medium",
            reversibility="dangerous",
            evidence=[f"git 歷史中有 {lb['bytes']} bytes 大型 blob"],
            action=f"git filter-repo --path '{lb.get('path','?')}' --invert-paths  # 改寫歷史，危險",
            risk="高（改寫 git 歷史，需團隊協調強推）",
            user_decision=True)

    # 依賴
    deps = audit.get("dependencies", {})
    if deps.get("has_pyproject") and deps.get("has_requirements_txt"):
        add(path="requirements.txt", category="review", severity="medium",
            reversibility="safe-manual",
            evidence=["同時有 pyproject.toml 與 requirements.txt（uv 以 pyproject 為準）"],
            action="rm requirements.txt  # 確認部署腳本不再依賴後執行",
            recommendation="統一以 pyproject.toml + uv.lock 為單一來源",
            impact="避免依賴定義分歧",
            risk="中（部署腳本可能仍依賴 requirements.txt）",
            doc_update=True,
            user_decision=True)
    if deps.get("has_pyproject") and not deps.get("has_uv_lock"):
        add(path="uv.lock", category="migrate", severity="high",
            reversibility="safe-auto",
            evidence=["有 pyproject.toml 但缺 uv.lock"],
            action="uv sync",
            recommendation="補上 lockfile 以鎖定依賴版本",
            impact="可重現的建置環境",
            risk="低",
            user_decision=False)
    if deps.get("has_poetry_lock"):
        add(path="poetry.lock", category="delete", severity="medium",
            reversibility="safe-manual",
            evidence=["poetry 已被本專案規範禁用"],
            action="rm poetry.lock  # 確認已遷移到 uv 後執行",
            user_decision=True)

    # 文件健康
    dh = audit.get("doc_health", {})
    for missing, key in [("README.md", "has_readme"), (".gitignore", "has_gitignore")]:
        if not dh.get(key):
            add(path=missing, category="migrate", severity="high",
                reversibility="safe-auto",
                evidence=[f"缺少 {missing}"],
                action=f"touch {missing}  # 並補上內容",
                doc_update=True,
                user_decision=True)

    return findings


def cross_findings(cross: dict) -> list[dict]:
    out = []
    for i, item in enumerate(cross.get("tcoe_migration_candidates", []), 1):
        out.append(with_meta({
            "id": f"X{i:03d}",
            "path": f"{item['repo']}: {item['path']}",
            "category": "migrate",
            "severity": "high",
            "reversibility": "needs-review",
            "evidence": [item["suggestion"]],
            "action": f"# 改用 tcoe.* 共用套件取代 {item['path']}（依 tcoe-infra 遷移計劃）",
            "recommendation": "移除本地副本，改 import tcoe.* 共用套件",
            "impact": "消除跨 repo 重複，集中維護於 tcoe-infra",
            "risk": "中（需確認 API 相容並更新 import）",
            "doc_update": True,
            "user_decision": True,
        }))
    for i, item in enumerate(cross.get("content_identical", [])[:20], 1):
        repos = sorted({o["repo"] for o in item["occurrences"]})
        out.append(with_meta({
            "id": f"XD{i:03d}",
            "path": item["occurrences"][0]["rel"],
            "category": "review",
            "severity": "medium",
            "reversibility": "needs-review",
            "evidence": [f"跨 repo 內容完全相同: {', '.join(repos)}",
                         f"{item['bytes']} bytes"],
            "action": f"# 考慮把 {item['occurrences'][0]['rel']} 抽到 tcoe.* 共用套件",
            "recommendation": "若為共用邏輯，搬到 tcoe-infra 共用套件",
            "impact": "減少複製貼上，單一來源維護",
            "risk": "中",
            "user_decision": True,
        }))
    return out


# ----- 評分 -----

def health_score(findings: list[dict]) -> int:
    weight = {"blocker": 20, "high": 5, "medium": 2, "low": 0.5, "info": 0}
    cap = {"blocker": 60, "high": 30, "medium": 20, "low": 10, "info": 0}
    by_sev = {}
    for f in findings:
        by_sev.setdefault(f["severity"], 0)
        by_sev[f["severity"]] += weight.get(f["severity"], 0)
    deduction = sum(min(v, cap.get(k, 0)) for k, v in by_sev.items())
    return max(0, int(100 - min(deduction, 100)))


def git_val(audit, key):
    g = audit.get("git") or {}
    return g.get(key, "?")


def group_phases(findings: list[dict]) -> dict:
    g = {}
    for f in findings:
        g.setdefault(f.get("phase", assign_phase(f)), []).append(f)
    return g


# ----- Markdown 輸出 -----

def render_md(audit: dict, findings: list[dict], cross) -> str:
    repo = audit["repo"]["name"]
    score = health_score(findings)
    decisions = [f for f in findings if f.get("user_decision")]
    auto = [f for f in findings if not f.get("user_decision")]
    docs = [f for f in findings if f.get("doc_update")]
    rec_adopt = [f for f in decisions if f.get("rec") == "adopt"]
    rec_skip = [f for f in decisions if f.get("rec") == "skip"]
    cat = {}
    for f in findings:
        cat[f["category"]] = cat.get(f["category"], 0) + 1
    phases = group_phases(findings)

    by_sev = {"blocker": [], "high": [], "medium": [], "low": [], "info": []}
    for f in findings:
        by_sev.setdefault(f["severity"], []).append(f)

    L = []
    L += [
        f"# 倉庫整潔度稽核報告 — `{repo}`",
        "",
        f"_產生時間 {audit['timestamp']}_  ",
        f"**健康分數：{score} / 100**",
        "",
        "---",
        "",
        "## 0. 優化計劃說明（請先閱讀）",
        "",
        "本報告由 `repo-hygiene-audit` 自動產生，目的在找出倉庫中**可清理、需更新、"
        "或需人工判斷**的內容，並給出分階段的整理建議。",
        "",
        "### 重點：我已經幫你把決定做好了",
        "",
        f"為了讓你「**用最少的決定、得到最大的整理效益**」，每一項發現我都先填好了"
        f"**推薦決策**：",
        "",
        f"- 推薦 **採用**：**{len(rec_adopt)}** 項（低/中風險、可逆，建議直接做）",
        f"- 推薦 **略過**：**{len(rec_skip)}** 項（不可逆或高風險，預設先不動）",
        "",
        "> 你最省事的做法：**直接全部接受推薦**，只針對少數你不同意的項目改成「略過」即可。"
        "在 HTML 報告裡按一下「套用全部推薦」就會自動勾選。",
        "",
        "### HTML 報告的勾選有什麼用？",
        "",
        "在 `report.html` 裡，第 1 節每一列都有 **採用 / 略過** 兩個選項，純粹是**視覺化的決策記錄**，"
        "方便你在審閱時逐項標記要不要做。",
        "",
        "- 勾選只存在於目前瀏覽器分頁，**不會寫回 HTML 檔、不會產生任何腳本**；重新整理就會清空。",
        "- 上方四顆按鈕：**套用全部推薦 / 全選採用 / 全部略過 / 清除**，只是批次幫你切換勾選狀態。",
        "- 真正的清理請依第 3 節「分階段執行計劃」逐條指令，在你的終端機親手執行。",
        "",
        "### 本次概況",
        "",
        f"- 總發現：**{len(findings)}** 項",
        f"- 需你決定：**{len(decisions)}** 項（其中推薦採用 {len(rec_adopt)}、推薦略過 {len(rec_skip)}）",
        f"- 可自動安全處理：**{len(auto)}** 項",
        f"- 需更新/調整的文件：**{len(docs)}** 項（見第 5 節清單）",
        "- 分類統計：" + "、".join(f"{CAT_ZH.get(k,k)} {v}" for k, v in sorted(cat.items())),
        "",
        "### 整體建議 / 影響 / 風險",
        "",
        "| 面向 | 說明 |",
        "|---|---|",
        "| **建議** | 直接套用全部推薦，再依 Phase 0→8 順序執行；先安全自動清理，最後做跨 repo 遷移 |",
        "| **預期影響** | 倉庫體積縮小、消除重複與棄用程式碼、文件與工具鏈對齊、跨 repo 單一真實來源 |",
        "| **主要風險** | 平行實作可能仍被引用（需確認）；改寫 git 歷史不可逆；部署腳本可能依賴 requirements.txt |",
        "| **降風險作法** | Phase 0 強制備份；刪除先 dry-run；每階段獨立 commit；危險操作（歷史改寫）獨立執行並需團隊協調 |",
        "",
        "---",
        "",
        "## 1. ⚠️ 需要你決定的事項",
        "",
        f"_共 {len(decisions)} 項。預設推薦：採用 {len(rec_adopt)}、略過 {len(rec_skip)}。"
        f"不同意推薦時才需要動手改。_",
        "",
        "| ID | 路徑 | 嚴重度 | 類別 | 證據 | 建議 | 影響 | 風險 | 推薦 | 建議指令 |",
        "|----|------|--------|------|------|------|------|------|------|----------|",
    ]
    for f in decisions[:120]:
        ev = "; ".join(f["evidence"])[:60]
        rec = "✅ 採用" if f.get("rec") == "adopt" else "⏭️ 略過"
        L.append(
            f"| {f['id']} | `{f['path']}` | {SEV_ZH.get(f['severity'],f['severity'])} "
            f"| {CAT_ZH.get(f['category'],f['category'])} | {ev} "
            f"| {f.get('recommendation','')} | {f.get('impact','')} | {f.get('risk','')} "
            f"| {rec} | `{f['action']}` |"
        )
    if len(decisions) > 120:
        L.append(f"\n_… 另有 {len(decisions)-120} 項，詳見 HTML 報告。_\n")

    L += [
        "",
        "## 2. 立即可做的快速清理（Top 5）",
        "",
    ]
    for f in auto[:5]:
        L.append(f"- **{f['id']}** `{f['action']}` — {'; '.join(f['evidence'])[:70]}")
    if not auto:
        L.append("- （無可自動安全處理的項目）")

    # ----- 第 3 節：分階段執行計劃（詳細）-----
    L += ["", "## 3. 分階段執行計劃", ""]
    L += [
        "每個階段獨立 commit（方便 `git revert`）。刪除類指令一律先 dry-run，確認後再真正執行。",
        "",
        "### Phase 0 — 備份（必做）",
        "```bash",
        f"# 1) 整包打包備份",
        f"tar -czf ~/repo-backup-{repo}-$(date +%Y%m%d).tar.gz -C $(dirname $PWD) {repo}",
        "# 2) 暫存未提交變更",
        "git stash push -u -m \"pre-audit-$(date +%Y%m%d)\" || true",
        "# 3) 記錄目前 commit，供回滾",
        "git rev-parse HEAD > .audit-snapshot-sha",
        "```",
        f"- **建議**：{PHASE_META[0][1]}",
        f"- **影響**：{PHASE_META[0][2]} **風險**：{PHASE_META[0][3]}",
        "",
    ]

    for ph in range(1, 8):
        title, advice, impact, risk = PHASE_META[ph]
        items = phases.get(ph, [])
        L.append(f"### Phase {ph} — {title}（{len(items)} 項）")
        L.append(f"- **建議**：{advice}")
        L.append(f"- **影響**：{impact} **風險**：{risk}")
        if items:
            L.append("")
            L.append("| ID | 路徑 | 推薦 | 風險 | 指令 / 步驟 |")
            L.append("|----|------|------|------|-------------|")
            for f in items[:40]:
                rec = "✅ 採用" if f.get("rec") == "adopt" else "⏭️ 略過"
                L.append(f"| {f['id']} | `{f['path']}` | {rec} | {f.get('risk','')[:14]} "
                         f"| `{f['action']}` |")
            if len(items) > 40:
                L.append(f"\n_… 另有 {len(items)-40} 項，詳見 HTML。_")
            # 可直接執行的指令彙整成腳本片段
            exec_cmds = [f["action"] for f in items
                         if f.get("rec") == "adopt" and is_exec(f["action"])
                         and f.get("reversibility") != "dangerous"]
            if exec_cmds:
                L.append("")
                L.append("<details><summary>本階段「推薦採用且可直接執行」的指令（dry-run）</summary>")
                L.append("")
                L.append("```bash")
                for c in exec_cmds[:40]:
                    L.append(f"echo + {c}   # 確認後移除 echo 再執行")
                L.append("```")
                L.append("</details>")
        L.append("")

    L += [
        "### Phase 8 — 驗證",
        "```bash",
        "uv run ruff check .",
        "uv run pytest",
        "```",
        f"- **建議**：{PHASE_META[8][1]}",
        f"- **影響**：{PHASE_META[8][2]} **風險**：{PHASE_META[8][3]}",
        "",
    ]

    # ----- 第 4 節：詳細發現 -----
    L += ["## 4. 詳細發現（依嚴重度）", ""]
    for sev in ["blocker", "high", "medium", "low", "info"]:
        items = by_sev.get(sev, [])
        if not items:
            continue
        L.append(f"### {SEV_ZH[sev]} ({len(items)})")
        L.append("")
        for f in items[:40]:
            L.append(
                f"- **{f['id']}** `{f['path']}` — {CAT_ZH.get(f['category'],f['category'])} ｜ "
                f"{'; '.join(f['evidence'])[:100]} → `{f['action']}`"
            )
        if len(items) > 40:
            L.append(f"  _… 另有 {len(items)-40} 項。_")
        L.append("")

    # ----- 第 5 節：需更新/調整的文件清單 -----
    L += ["## 5. 需更新或調整的文件清單", ""]
    if docs:
        L.append("以下文件偵測到需要更新或調整（過期內容、失效連結、與規範不符等）：")
        L.append("")
        L.append("| ID | 文件 | 為什麼要更新 | 建議動作 |")
        L.append("|----|------|--------------|----------|")
        for f in docs:
            L.append(
                f"| {f['id']} | `{f['path']}` | {'; '.join(f['evidence'])[:80]} "
                f"| `{f['action']}` |"
            )
    else:
        L.append("（未偵測到需更新的文件。）")
    L.append("")

    # ----- 第 6 節：跨 repo -----
    if cross:
        L += [
            "## 6. 跨 Repo 發現",
            "",
            f"- 已稽核 repo：{', '.join(r['name'] for r in cross['repos'])}",
            f"- tcoe.* 遷移候選：{len(cross.get('tcoe_migration_candidates', []))} 項",
            f"- 跨 repo 內容完全相同的檔案：{len(cross.get('content_identical', []))} 項",
            f"- 同名檔案（潛在共用候選）：{len(cross.get('duplicate_basenames', []))} 項",
            "",
        ]

    L += [
        "## 7. 回滾（Rollback）",
        "```bash",
        "git reset --hard $(cat .audit-snapshot-sha)",
        "git stash pop  # 若 Phase 0 有 stash",
        f"# 或完整還原: tar -xzf ~/repo-backup-{repo}-<date>.tar.gz",
        "```",
    ]
    return "\n".join(L) + "\n"


# ----- HTML 輸出 -----

def render_html(audit: dict, findings: list[dict], cross) -> str:
    repo = audit["repo"]["name"]
    score = health_score(findings)
    decisions = [f for f in findings if f.get("user_decision")]
    auto = [f for f in findings if not f.get("user_decision")]
    docs = [f for f in findings if f.get("doc_update")]
    rec_adopt = [f for f in decisions if f.get("rec") == "adopt"]
    rec_skip = [f for f in decisions if f.get("rec") == "skip"]
    phases = group_phases(findings)
    by_sev = {}
    for f in findings:
        by_sev.setdefault(f["severity"], []).append(f)
    cat_counts = {}
    for f in findings:
        cat_counts[f["category"]] = cat_counts.get(f["category"], 0) + 1

    # Fluent 語意色（深字配淺底）
    sev_color = {"blocker": "#D13438", "high": "#CA5010", "medium": "#FFB900",
                 "low": "#107C10", "info": "#0078D4"}
    sev_fg = {"blocker": "#fff", "high": "#fff", "medium": "#3B3A39",
              "low": "#fff", "info": "#fff"}

    def esc(s):
        return html.escape(str(s))

    def attr(s):
        return html.escape(str(s), quote=True)

    decision_rows = ""
    for f in decisions:
        rec = f.get("rec", "adopt")
        adopt_chk = "checked" if rec == "adopt" else ""
        skip_chk = "checked" if rec == "skip" else ""
        danger = "1" if f.get("reversibility") == "dangerous" else "0"
        execf = "1" if is_exec(f["action"]) else "0"
        decision_rows += (
            f"<tr data-fid='{attr(f['id'])}' data-action=\"{attr(f['action'])}\" "
            f"data-danger='{danger}' data-exec='{execf}' data-rec='{attr(rec)}'>"
            f"<td>{esc(f['id'])}</td>"
            f"<td><code>{esc(f['path'])}</code></td>"
            f"<td><span class='badge' style='background:{sev_color.get(f['severity'],'#888')};color:{sev_fg.get(f['severity'],'#fff')}'>{esc(SEV_ZH.get(f['severity'],f['severity']))}</span></td>"
            f"<td>{esc(CAT_ZH.get(f['category'],f['category']))}</td>"
            f"<td class='ev'>{esc('; '.join(f['evidence']))}</td>"
            f"<td>{esc(f.get('recommendation',''))}</td>"
            f"<td>{esc(f.get('impact',''))}</td>"
            f"<td>{esc(f.get('risk',''))}</td>"
            f"<td class='dec'>"
            f"<label><input type='radio' name='dec_{attr(f['id'])}' value='adopt' {adopt_chk}> 採用</label><br>"
            f"<label><input type='radio' name='dec_{attr(f['id'])}' value='skip' {skip_chk}> 略過</label>"
            f"<div class='recnote'>推薦：{'採用' if rec=='adopt' else '略過'}</div></td>"
            f"<td><code>{esc(f['action'])}</code></td></tr>"
        )

    doc_rows = "".join(
        f"<tr><td>{esc(f['id'])}</td>"
        f"<td><code>{esc(f['path'])}</code></td>"
        f"<td>{esc('; '.join(f['evidence']))}</td>"
        f"<td><code>{esc(f['action'])}</code></td></tr>"
        for f in docs
    ) or "<tr><td colspan='4'>（未偵測到需更新的文件）</td></tr>"

    sev_sections = ""
    for sev in ["blocker", "high", "medium", "low", "info"]:
        items = by_sev.get(sev, [])
        if not items:
            continue
        rows = "".join(
            f"<li><strong>{esc(f['id'])}</strong> <code>{esc(f['path'])}</code> "
            f"— {esc(CAT_ZH.get(f['category'],f['category']))} ｜ {esc('; '.join(f['evidence']))} → "
            f"<code>{esc(f['action'])}</code></li>"
            for f in items
        )
        sev_sections += (
            f"<details><summary><span class='badge' "
            f"style='background:{sev_color[sev]};color:{sev_fg[sev]}'>{esc(SEV_ZH[sev])}</span> "
            f"({len(items)})</summary><ul>{rows}</ul></details>"
        )

    # 分階段執行計劃（HTML，含每階段項目表）
    def phase_block(ph):
        title, advice, impact, risk = PHASE_META[ph]
        items = phases.get(ph, [])
        rows = "".join(
            f"<tr><td>{esc(f['id'])}</td><td><code>{esc(f['path'])}</code></td>"
            f"<td>{'採用' if f.get('rec')=='adopt' else '略過'}</td>"
            f"<td>{esc(f.get('risk','')[:18])}</td>"
            f"<td><code>{esc(f['action'])}</code></td></tr>"
            for f in items[:40]
        )
        table = (f"<table><thead><tr><th>ID</th><th>路徑</th><th>推薦</th><th>風險</th>"
                 f"<th>指令 / 步驟</th></tr></thead><tbody>{rows}</tbody></table>"
                 if items else "<p class='meta'>（本階段無發現）</p>")
        return (f"<div class='phase'><h3>Phase {ph} — {esc(title)}（{len(items)} 項）</h3>"
                f"<p class='meta'><strong>建議：</strong>{esc(advice)}<br>"
                f"<strong>影響：</strong>{esc(impact)} <strong>風險：</strong>{esc(risk)}</p>"
                f"{table}</div>")

    phase_blocks = "".join(phase_block(ph) for ph in range(1, 8))

    cross_block = ""
    if cross:
        tcoe = cross.get("tcoe_migration_candidates", [])
        identical = cross.get("content_identical", [])
        dup_base = cross.get("duplicate_basenames", [])
        tcoe_html = "".join(
            f"<li><code>{esc(t['repo'])}</code>: <code>{esc(t['path'])}</code> "
            f"→ <em>{esc(t['suggestion'])}</em></li>" for t in tcoe[:60]
        )
        identical_html = "".join(
            f"<li>{esc(i['bytes'])} bytes — <code>{esc(i['occurrences'][0]['rel'])}</code> "
            f"於 {esc(', '.join(sorted({o['repo'] for o in i['occurrences']})))}</li>"
            for i in identical[:30]
        )
        cross_block = f"""
        <h2>6. 跨 Repo 發現</h2>
        <p>已稽核 repo：{esc(', '.join(r['name'] for r in cross['repos']))}</p>
        <details open><summary>tcoe.* 遷移候選 ({len(tcoe)})</summary>
          <ul>{tcoe_html}</ul></details>
        <details><summary>跨 repo 內容完全相同的檔案 ({len(identical)})</summary>
          <ul>{identical_html}</ul></details>
        <details><summary>同名檔案（潛在共用候選, {len(dup_base)}）</summary>
          <ul>{''.join(f'<li><code>{esc(d["basename"])}</code> 於 {esc(", ".join(d["repos"]))}</li>' for d in dup_base[:60])}</ul></details>
        """

    counts = {sev: len(by_sev.get(sev, [])) for sev in ["blocker", "high", "medium", "low", "info"]}
    cat_summary = "、".join(f"{CAT_ZH.get(k,k)} {v}" for k, v in sorted(cat_counts.items()))
    score_color = '#107C10' if score >= 80 else '#FFB900' if score >= 50 else '#D13438'
    g = audit.get("git") or {}

    return f"""<!doctype html>
<html lang="zh-Hant"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>倉庫整潔度稽核 — {esc(repo)}</title>
<style>
  :root {{
    --ms-blue: #0078D4; --ms-blue-hover: #106EBE; --ms-blue-dark: #004578;
    --ms-blue-light: #DEECF9; --ms-blue-bg: #F3F8FC;
    --white: #FFFFFF; --gray-10: #FAF9F8; --gray-20: #F3F2F1; --gray-30: #EDEBE9;
    --gray-40: #D2D0CE; --gray-60: #A19F9D; --gray-90: #605E5C; --gray-130: #323130;
    --gray-160: #1B1A19; --green: #107C10; --green-bg: #DFF6DD; --red: #D13438;
    --red-bg: #FDE7E9; --yellow: #FFB900; --yellow-bg: #FFF4CE; --purple: #5C2D91;
  }}
  * {{ box-sizing: border-box; }}
  body {{ font: 15px/1.5 'Segoe UI', 'Microsoft JhengHei', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
    background: var(--gray-10); color: var(--gray-130); margin: 0; }}
  .wrap {{ max-width: 1320px; margin-inline: auto; padding: 0 32px 64px; }}
  nav {{ height: 48px; background: var(--white); border-bottom: 1px solid var(--gray-30);
    display: flex; align-items: center; gap: 12px; padding: 0 32px; position: sticky;
    top: 0; z-index: 20; }}
  nav .logo {{ width: 20px; height: 20px; }}
  nav .sep {{ width: 1px; height: 24px; background: var(--gray-40); }}
  nav .prod {{ font-size: 14px; font-weight: 600; color: var(--gray-160); }}
  nav .repo {{ font-size: 13px; color: var(--gray-90); margin-left: auto;
    font-family: 'Cascadia Code','Consolas','SF Mono',monospace; }}
  .hero {{ background: linear-gradient(135deg,#0078D4 0%,#5C2D91 50%,#0078D4 100%);
    color: #fff; padding: 40px 48px; position: relative; overflow: hidden; }}
  .hero::after {{ content:''; position:absolute; right:-80px; top:-80px; width:320px;
    height:320px; border-radius:50%; background:radial-gradient(circle,rgba(255,255,255,0.12),transparent 70%); }}
  .hero h1 {{ font-size: 32px; font-weight: 600; letter-spacing: -0.5px; margin: 0 0 6px; }}
  .hero .sub {{ font-size: 13px; opacity: 0.9; font-family:'Cascadia Code','Consolas','SF Mono',monospace; }}
  .hero .kpis {{ display: flex; gap: 28px; margin-top: 22px; align-items: flex-end; flex-wrap: wrap; }}
  .hero .score {{ font-size: 44px; font-weight: 700; line-height: 1; color: {score_color}; }}
  .hero .scorecard {{ background: rgba(255,255,255,0.95); border-radius: 8px;
    padding: 14px 20px; text-align: center; color: var(--gray-130); }}
  .hero .scorecard .lbl {{ font-size: 11px; font-weight: 600; letter-spacing: 0.8px;
    text-transform: uppercase; color: var(--gray-90); margin-top: 4px; }}
  .counts {{ display: flex; gap: 8px; flex-wrap: wrap; }}
  h2 {{ font-size: 28px; font-weight: 600; letter-spacing: -0.3px; color: var(--gray-160);
    border-bottom: 1px solid var(--gray-30); padding-bottom: 8px; margin: 48px 0 16px; }}
  h3 {{ font-size: 16px; font-weight: 600; color: var(--gray-160); }}
  .meta {{ color: var(--gray-90); font-size: 13px; }}
  .badge {{ display: inline-block; padding: 2px 10px; border-radius: 12px;
    font-weight: 600; font-size: 12px; }}
  table {{ border-collapse: collapse; width: 100%; margin: 16px 0;
    background: var(--white); font-size: 13px; border: 1px solid var(--gray-30);
    border-radius: 8px; overflow: hidden; }}
  th, td {{ padding: 10px 16px; border-bottom: 1px solid var(--gray-20);
    text-align: left; vertical-align: top; overflow-wrap: anywhere; word-break: break-word; }}
  th {{ background: var(--gray-10); font-size: 12px; font-weight: 600;
    letter-spacing: 0.4px; text-transform: uppercase; color: var(--gray-90);
    position: sticky; top: 48px; z-index: 1; }}
  tr:hover td {{ background: var(--gray-10); }}
  th:first-child, td:first-child {{ white-space: nowrap; }}
  td.ev {{ color: var(--gray-90); max-width: 240px; }}
  td.dec {{ white-space: nowrap; }}
  .recnote {{ color: var(--gray-90); font-size: 12px; margin-top: 4px; }}
  code {{ background: var(--gray-20); padding: 1px 6px; border-radius: 4px;
    font-family: 'Cascadia Code','Consolas','SF Mono',monospace; font-size: 0.85em;
    color: var(--ms-blue-dark); overflow-wrap: anywhere; word-break: break-word; }}
  details {{ background: var(--white); padding: 12px 16px; border-radius: 8px;
    margin: 8px 0; border: 1px solid var(--gray-30); }}
  details summary {{ cursor: pointer; font-weight: 600; color: var(--gray-160); }}
  ul {{ margin: 8px 0 0; padding-left: 24px; }}
  li {{ margin: 4px 0; }}
  .card {{ background: var(--white); border: 1px solid var(--gray-30);
    border-radius: 8px; padding: 24px; margin: 16px 0; }}
  .intro table {{ margin: 12px 0; }}
  .phase {{ background: var(--white); border: 1px solid var(--gray-30);
    border-left: 3px solid var(--ms-blue); padding: 16px 20px; margin: 12px 0;
    border-radius: 0 8px 8px 0; }}
  .phase h3 {{ margin: 0 0 6px; color: var(--ms-blue-dark); }}
  .phase table {{ font-size: 12px; }}
  pre {{ background: var(--gray-160); color: rgba(255,255,255,0.88); border-radius: 8px;
    padding: 16px; overflow-x: auto; font-family: 'Cascadia Code','Consolas','SF Mono',monospace;
    font-size: 12px; line-height: 1.7; }}
  .warn {{ background: var(--white); border: 1px solid var(--gray-30);
    border-left: 4px solid var(--red); padding: 20px 24px; border-radius: 0 8px 8px 0;
    margin: 16px 0; }}
  .docs {{ background: var(--white); border: 1px solid var(--gray-30);
    border-left: 4px solid var(--ms-blue); padding: 20px 24px; border-radius: 0 8px 8px 0;
    margin: 16px 0; }}
  .kv th {{ width: 18%; color: var(--gray-90); text-transform: none; letter-spacing: 0; }}
  @media (max-width: 768px) {{
    .wrap {{ padding: 0 16px 48px; }} nav, .hero {{ padding-left: 16px; padding-right: 16px; }}
    .hero h1 {{ font-size: 26px; }} h2 {{ font-size: 22px; }}
  }}
</style>
</head><body>
<nav>
  <svg class="logo" viewBox="0 0 20 20" fill="none">
    <rect x="1" y="1" width="8.5" height="8.5" fill="#F25022"/>
    <rect x="10.5" y="1" width="8.5" height="8.5" fill="#7FBA00"/>
    <rect x="1" y="10.5" width="8.5" height="8.5" fill="#00A4EF"/>
    <rect x="10.5" y="10.5" width="8.5" height="8.5" fill="#FFB900"/>
  </svg>
  <div class="sep"></div>
  <span class="prod">倉庫整潔度稽核</span>
  <span class="repo">{esc(audit['repo']['path'])}</span>
</nav>

<div class="hero">
  <h1>倉庫整潔度稽核報告 — {esc(repo)}</h1>
  <div class="sub">產生時間 {esc(audit['timestamp'])}</div>
  <div class="kpis">
    <div class="scorecard"><div class="score">{score}</div><div class="lbl">健康分數 / 100</div></div>
    <div class="counts">
      {''.join(f'<span class="badge" style="background:{sev_color[s]};color:{sev_fg[s]}">{SEV_ZH[s]}: {counts[s]}</span>' for s in ["blocker","high","medium","low","info"])}
    </div>
  </div>
</div>

<div class="wrap">

<h2>0. 優化計劃說明（請先閱讀）</h2>
<div class="card">
  <p>本報告由 <code>repo-hygiene-audit</code> 自動產生，目的在找出倉庫中
  <strong>可清理、需更新、或需人工判斷</strong>的內容，並給出分階段整理建議。</p>

  <h3>重點：我已經幫你把決定做好了</h3>
  <p>為了讓你「<strong>用最少的決定、得到最大的整理效益</strong>」，每一項發現都先填好了
  <strong>推薦決策</strong>：</p>
  <ul>
    <li>推薦 <strong>採用</strong>：<strong>{len(rec_adopt)}</strong> 項（低/中風險、可逆，建議直接做）</li>
    <li>推薦 <strong>略過</strong>：<strong>{len(rec_skip)}</strong> 項（不可逆或高風險，預設先不動）</li>
  </ul>
  <h3>勾選「採用 / 略過」有什麼用？</h3>
  <ul>
    <li>純粹是<strong>視覺化的決策記錄</strong>，方便你逐項標記要不要做；預設已依推薦勾好。</li>
    <li>勾選只存在於目前瀏覽器分頁，<strong>不會寫回 HTML 檔、不會產生任何腳本</strong>；重新整理就會清空。</li>
    <li>真正的清理請依第 3 節「分階段執行計劃」的指令，在你的終端機親手執行。</li>
  </ul>

  <h3>本次概況</h3>
  <ul>
    <li>總發現：<strong>{len(findings)}</strong> 項</li>
    <li>需你決定：<strong>{len(decisions)}</strong> 項（推薦採用 {len(rec_adopt)}、推薦略過 {len(rec_skip)}）</li>
    <li>可自動安全處理：<strong>{len(auto)}</strong> 項</li>
    <li>需更新/調整的文件：<strong>{len(docs)}</strong> 項（見第 5 節）</li>
    <li>分類統計：{esc(cat_summary)}</li>
  </ul>

  <h3>整體建議 / 影響 / 風險</h3>
  <table class="kv">
    <tr><th>建議</th><td>直接套用全部推薦，再依 Phase 0→8 順序執行：先安全自動清理，最後做跨 repo 遷移。</td></tr>
    <tr><th>預期影響</th><td>倉庫體積縮小、消除重複與棄用程式碼、文件與工具鏈對齊、跨 repo 單一真實來源。</td></tr>
    <tr><th>主要風險</th><td>平行實作可能仍被引用（需確認）；改寫 git 歷史不可逆；部署腳本可能仍依賴 requirements.txt。</td></tr>
    <tr><th>降風險作法</th><td>Phase 0 強制備份；刪除先 dry-run；每階段獨立 commit；歷史改寫獨立執行並需團隊協調。</td></tr>
  </table>
</div>

<div class="warn">
  <h2 style="margin-top:0;border:none">1. 需要你決定的事項（{len(decisions)}）</h2>
  <table>
    <thead><tr><th>ID</th><th>路徑</th><th>嚴重度</th><th>類別</th><th>證據</th>
      <th>建議</th><th>影響</th><th>風險</th><th>決策（採用/略過）</th><th>建議指令</th></tr></thead>
    <tbody>{decision_rows}</tbody>
  </table>
</div>

<h2>3. 分階段執行計劃</h2>
<div class="phase"><h3>Phase 0 — 備份（必做）</h3>
<p class="meta"><strong>建議：</strong>{esc(PHASE_META[0][1])}<br>
<strong>影響：</strong>{esc(PHASE_META[0][2])} <strong>風險：</strong>{esc(PHASE_META[0][3])}</p>
<pre># 1) 整包打包備份
tar -czf ~/repo-backup-{esc(repo)}-$(date +%Y%m%d).tar.gz -C $(dirname $PWD) {esc(repo)}
# 2) 暫存未提交變更
git stash push -u -m "pre-audit-$(date +%Y%m%d)" || true
# 3) 記錄目前 commit，供回滾
git rev-parse HEAD &gt; .audit-snapshot-sha</pre></div>
{phase_blocks}
<div class="phase"><h3>Phase 8 — 驗證</h3>
<p class="meta"><strong>建議：</strong>{esc(PHASE_META[8][1])}<br>
<strong>影響：</strong>{esc(PHASE_META[8][2])} <strong>風險：</strong>{esc(PHASE_META[8][3])}</p>
<pre>uv run ruff check .
uv run pytest</pre></div>

<h2>4. 詳細發現（依嚴重度）</h2>
{sev_sections}

<h2>5. 需更新或調整的文件清單</h2>
<div class="docs">
  <p>以下文件偵測到需要更新或調整（過期內容、失效連結、與規範不符等）：</p>
  <table>
    <thead><tr><th>ID</th><th>文件</th><th>為什麼要更新</th><th>建議動作</th></tr></thead>
    <tbody>{doc_rows}</tbody>
  </table>
</div>

{cross_block}

<h2>7. 回滾（Rollback）</h2>
<pre>git reset --hard $(cat .audit-snapshot-sha)
git stash pop  # 若 Phase 0 有 stash
# 完整還原: tar -xzf ~/repo-backup-{esc(repo)}-&lt;date&gt;.tar.gz</pre>

<p class="meta" style="margin-top:48px">repo-hygiene-audit · schema {esc(audit.get('schema',''))}</p>

</div><!-- /.wrap -->
</body></html>
"""


# ----- main -----

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("audit_json")
    ap.add_argument("--cross", help="跨 repo JSON")
    ap.add_argument("--interpretation", help="LLM 分類後的 findings JSON")
    ap.add_argument("-o", "--out", default="./reports", help="輸出目錄")
    args = ap.parse_args()

    audit = json.loads(Path(args.audit_json).read_text())
    cross = json.loads(Path(args.cross).read_text()) if args.cross else None

    if args.interpretation:
        findings = [with_meta(f) for f in json.loads(Path(args.interpretation).read_text())["findings"]]
    else:
        findings = classify(audit)
        if cross:
            findings += cross_findings(cross)

    # 補上 phase（供分階段計劃使用）
    for f in findings:
        f.setdefault("phase", assign_phase(f))

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    (out_dir / "report.md").write_text(render_md(audit, findings, cross))
    (out_dir / "report.html").write_text(render_html(audit, findings, cross))
    (out_dir / "findings.json").write_text(
        json.dumps({"findings": findings, "score": health_score(findings)},
                   indent=2, ensure_ascii=False)
    )
    print(f"已輸出: {out_dir}/report.md")
    print(f"已輸出: {out_dir}/report.html")
    print(f"已輸出: {out_dir}/findings.json")
    print(f"健康分數: {health_score(findings)}")


if __name__ == "__main__":
    main()

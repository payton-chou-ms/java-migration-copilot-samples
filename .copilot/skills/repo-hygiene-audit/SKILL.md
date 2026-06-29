---
name: repo-hygiene-audit
description: >
  Audit a repository for cleanliness, duplicate content, stale structure, and migration debt.
  Output Markdown only (no HTML) in a compact OPTIMIZATION_PLAN style checklist.
  Use when user asks: audit repo, clean up repo, repo hygiene, 整理 repo, 分析 repo 是否乾淨,
  找舊的 code/doc, or /repo-hygiene-audit.
---

# Repo Hygiene Audit (Markdown Only)

This skill produces a single Markdown plan/checklist in the same style as OPTIMIZATION_PLAN.md.
Use MD check only. Do not generate interactive HTML.

## When to Invoke

Triggers:
- audit repo
- clean up repo
- repo hygiene
- 整理 repo
- 找舊的 code / doc
- stale docs
- /repo-hygiene-audit

## Output Contract (Mandatory)

Return one Markdown document only, with this structure:

1. Title + metadata block
2. Progress summary line
3. Current issues table (only unresolved items)
4. Acceptance criteria checklist (only unresolved items)
5. Execution plan by phase
6. Risk and rollback notes
7. Decision table (optional, concise)

Use concise wording and action-oriented checkboxes.
Do not include HTML, radio buttons, or browser-only controls.

## Required Format Template

Use this skeleton and fill with real findings:

```md
# <repo-name> Repo 優化計畫

**Repo:** <absolute-path>
**Scope:** <summary>
**目標讀者：** <owner/team>

> **進度更新 YYYY-MM-DD：** Phase 1 ✅ Phase 2 ⬜ ...

---

## 0. 現況（待修項目）

| 維度 | 觀察 |
|---|---|
| ... | ... |

---

## 1. 驗收標準（剩餘）

- [ ] ...
- [ ] ...

---

## 2. 執行計畫（剩餘項目）

### Phase N — <名稱>
- [ ] ...
- [ ] ...

---

## 3. 風險與回滾

- 主要風險：...
- 緩解作法：...
```

## Collection Workflow

1. Collect deterministic facts (size, duplicates, stale paths, suspicious files).
2. Classify each finding:
   - severity: blocker | high | medium | low | info
   - reversibility: safe-auto | safe-manual | needs-review | dangerous
   - action: delete | archive | migrate | refactor | keep
3. Convert findings into phase checklists with concrete commands.
4. Output Markdown only.

## Prioritization Rules

- Show only unresolved items by default.
- Collapse completed items into one progress summary line.
- Prefer irreversible tasks as unchecked with explicit warning.
- Prefer archive/move over delete when uncertainty exists.

## Safety Rules (Mandatory)

- Dry-run first for destructive operations.
- Backup before cleanup phases.
- One commit per phase.
- Never execute destructive actions automatically without explicit user approval.

## Command Style

When commands are needed in output, use short blocks like:

```bash
# dry-run example
find . -name '*.tmp' -print

# apply example (after approval)
find . -name '*.tmp' -delete
```

## Boundaries

- Markdown only. No report.html generation.
- No dual-model review gates required.
- No mutation by default unless user explicitly asks to apply changes.

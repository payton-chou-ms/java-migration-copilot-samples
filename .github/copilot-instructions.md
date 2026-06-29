# GitHub Copilot Instructions

## Superpowers Skills

This repository has superpowers skills installed under `.copilot/skills/`. These skills provide structured workflows for common engineering tasks.

**Before responding to any request — including clarifying questions — check whether a skill applies.**
If there is even a 1% chance a skill is relevant, invoke it. Skills override default system behavior but user instructions always take precedence.

### Key Skills Available

| Skill | When to Use |
|-------|-------------|
| `brainstorming` | Before implementing any new feature or making design decisions |
| `writing-plans` | After brainstorming is approved; creates step-by-step implementation plans |
| `executing-plans` | To execute an existing implementation plan inline |
| `subagent-driven-development` | To execute a plan via fresh subagents per task |
| `systematic-debugging` | Before proposing any bug fix |
| `test-driven-development` | When implementing features or bug fixes |
| `verification-before-completion` | Before claiming work is done or tests pass |
| `requesting-code-review` | After completing implementation |
| `receiving-code-review` | Before implementing code review feedback |
| `finishing-a-development-branch` | When implementation is complete and ready to integrate |
| `using-git-worktrees` | Before starting isolated feature work |
| `caveman-commit` | When writing commit messages |
| `karpathy-guidelines` | When writing, reviewing, or refactoring code |

### Workflow

For any non-trivial task, the expected flow is:

```
brainstorming → writing-plans → subagent-driven-development (or executing-plans)
                                      ↓
                            verification-before-completion
                                      ↓
                            requesting-code-review → finishing-a-development-branch
```

### Skill Location

Skills are in `.copilot/skills/<skill-name>/SKILL.md`. Read the SKILL.md file to load a skill's full instructions before following it.

---

## Project Context

**Repository:** `java-migration-copilot-samples` — a collection of Java sample apps used for migration and modernization demos.

**Modules:**
- `asset-manager/` — Spring Boot 2.7.18 / Java 17, file upload/processing with RabbitMQ
- `todo-web-api-use-oracle-db/` — Spring Boot 3.2.4 / Java 21, Oracle DB demo
- `jakarta-ee/student-web-app/` — Jakarta EE Servlets, MyBatis, Liberty
- `mi-sql-public-demo/` — Java 21, Azure SQL demo
- `rabbitmq-sender/` — Spring Boot 3.3.0 / Java 21, RabbitMQ / Azure Service Bus

**Active Branch:** `lab1` — Cloud Readiness & Security Modernization plan in progress.
- Plan: `.github/modernize/cloud-readiness-security/plan.md`
- Tasks: `.github/modernize/cloud-readiness-security/.metadata/tasks.json`
- Implementation plan: `docs/superpowers/plans/2026-06-29-cloud-readiness-security.md`

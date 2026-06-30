# GitHub Copilot Instructions

## Superpowers Skills

This repository uses the [Superpowers](https://github.com/obra/superpowers) skills framework.

**At the start of every conversation**, read and follow the `using-superpowers` skill:

```
.copilot/skills/using-superpowers/SKILL.md
```

Before any response or action, check whether a skill applies and invoke it. Even a 1% chance a skill might apply means you must invoke it.

## Available Skills

Skills are located in `.copilot/skills/`. Each skill is a directory containing a `SKILL.md` file.

To invoke a skill, read its `SKILL.md` and follow its instructions exactly.

| Skill | When to Use |
|-------|-------------|
| `using-superpowers` | **Default — start of every conversation** |
| `brainstorming` | Before writing any code — refine requirements through questions |
| `writing-plans` | After design approval — break work into bite-sized tasks |
| `subagent-driven-development` | Dispatch subagents per task with two-stage review |
| `executing-plans` | Batch execution with human checkpoints |
| `dispatching-parallel-agents` | 2+ independent tasks that can run concurrently |
| `test-driven-development` | During implementation — RED-GREEN-REFACTOR cycle |
| `systematic-debugging` | When encountering bugs or unexpected behavior |
| `verification-before-completion` | Before claiming work is complete or fixed |
| `requesting-code-review` | After completing implementation |
| `receiving-code-review` | When responding to review feedback |
| `using-git-worktrees` | Before feature work needing branch isolation |
| `finishing-a-development-branch` | When implementation is complete and ready to integrate |
| `writing-skills` | When creating or editing skill files |

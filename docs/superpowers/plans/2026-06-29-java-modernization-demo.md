# Java 現代化端對端展示 — 整合實作計畫（最少人工介入）

> **給代理執行者：** 必要子技能：請使用 superpowers:subagent-driven-development（建議）或 superpowers:executing-plans 逐一執行。步驟使用核取方塊（`- [ ]`）追蹤。

**目標：** 客戶以「最少決策」完成完整升級——兩個 Copilot extension 自動產出評估/計畫/任務，Superpowers 全自動執行（每 task：TDD→實作→verify→review）；版本升級與其他 Azure 遷移配方在同一條黃金路徑上「一起自動套用」，無逐任務核准。

**架構：** Copilot modernization 提供領域知識（AppCAT 評估、推薦任務、版本升級引擎、預定義配方），Copilot for Azure 處理部署/資源/Bicep，Superpowers 控制紀律（worktree/TDD/review/收尾）。

**技術堆疊：** Java 21、Maven、Spring Boot 3.3→3.4、OpenRewrite（modernization 升級引擎）、AppCAT、`@azure`、MCP（modernization + azure）。

## 全域限制 / 預設值（降低輸入）

- 目標 Java=21、Spring Boot=3.4；scope＝**整庫評估、執行升級 `rabbitmq-sender`**
- 配方＝rabbit-mq→service-bus、mi-servicebus、plaintext→keyvault
- 執行模式＝subagent-driven 全自動、無逐任務核准
- 「執行」＝ `mvn clean verify`，升級前後綠燈
- 不更動其他範例原始碼（但整庫納入評估）

## 黃金路徑（一條到底）

```
👤brainstorm approve → [整庫assess → 推薦task → 版本升級 → Azure配方 → verify] 全自動
→ 👤選執行方式 → using-git-worktrees → 自動執行 → 👤決定收尾
```

## 每步 human/auto 對照

| 階段 | 工具/Extension | human/auto |
|---|---|---|
| brainstorming 問答/approve/review spec | superpowers | 👤 |
| 整庫 assessment | modernization：AppCAT | auto |
| 推薦任務 | recommendMigrationTasks | auto |
| 版本升級 | gotoAgentMode（引擎=OpenRewrite） | auto |
| Azure 配方 | formula.run | auto |
| 選執行方式 | writing-plans | 👤 |
| 逐 task TDD/review | subagent-driven | auto |
| 收尾 | finishing | 👤 |

## Artifact Map

`assessment.md` → summary report → recommended-tasks → plan/tasks → diff/commits → verify 輸出

## 前置條件（避免額外提示）

兩 extension 已裝、Azure 已登入（若啟用 Azure 步驟）、Maven/JDK 就緒、repo 乾淨、scope 預選。

---

### 任務 1：環境＋基準建置
- [ ] **步1** `.devcontainer` 加 `"ghcr.io/devcontainers/features/java:1":{"installMaven":"true"}`
- [ ] **步2** `sudo apt-get install -y maven`；**步3** `cd rabbitmq-sender && mvn -q clean verify`（綠燈）→ commit

### 任務 2：整庫評估（auto）
- [ ] AppCAT 安裝 `migrate.java.appcat.install` → `migrate.assessment`（整庫）→ summary report → `recommendMigrationTasks` 匯出

### 任務 3：版本升級（auto，引擎=OpenRewrite）
- [ ] `appmod.javaUpgrade.gotoAgentMode`；等價 pom `rewrite-maven-plugin`＋`UpgradeToJava21`/`UpgradeSpringBoot_3_4` → `mvn rewrite:run` → verify → commit

### 任務 4：Azure 配方（auto，一起套用）
- [ ] `formula.run`：rabbit-mq→service-bus、mi-servicebus、plaintext→keyvault；`@azure` 產 Bicep
- [ ] 驗證：configs 已改、無明碼、Azure SDK 編譯過、Bicep 合法 → commit

### 任務 5：一鍵編排腳本
- [ ] `scripts/demo-modernize.sh` 串接 before-verify→rewrite→after-verify→diff（編排已生成 task，不取代評估流程）

### 任務 6：驗證＋收尾
- [ ] verification-before-completion → finishing-a-development-branch（👤）

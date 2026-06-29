# Java 應用程式現代化端對端展示 — 設計

日期：2026-06-29
狀態：已核准（自主進行；使用者稍後審閱）

## 目標

讓客戶以「最少決策」完成完整升級：由 GitHub Copilot 的兩個官方 extension 自動產生
評估 → 計畫 → 任務，再由 Superpowers 全自動逐任務執行（TDD→實作→verify→review），
人只在少數關卡介入。可一次只跑「版本升級」，也可同時自動套用其他遷移任務。

## 使用的兩個 Extension

### 1. GitHub Copilot modernization（`vscjava.migrate-java-to-azure` v1.21）
聊天參與者：`@azure`（含 modernization 指令）；命令前綴 `migrate.java.*`。核心功能：

- **Assessment（評估）**：`migrate.assessment`、`migrate.java.assessment.summaryReport`、
  匯入/匯出報告。底層用 **AppCAT for Java**（`migrate.java.appcat.install`）掃描相依、
  風險、可遷移點，產生 summary report。
- **Recommend Migration Tasks**：`migrate.java.recommendMigrationTasks` 依評估推薦任務。
- **Upgrade（版本升級）**：`appmod.javaUpgrade.gotoAgentMode`、`migrate.dotnet.upgrade`，
  升級 Java/Spring 版本。
- **Formulas / Skills（配方）**：`migrate.java.formula.run`、create/edit、Skills
  Marketplace、Copy to My Skills——可建立並套用自訂遷移配方。
- **內建技能（skills/）**：assessment、create-modernization-plan、
  creating-implementation-plan、dag-generation、implementing-code、quality-gates、
  runtime-validation、analyzing-architecture、building-java-knowledge-graph、
  team-charters、feature-inventory、project-decomposition 等 25 個。
- **預定義遷移配方（kb/）**：
  - 訊息：rabbit-mq→service-bus、kafka→eventhubs、activemq→servicebus、sqs→servicebus
  - 資料庫：oracle/db2/informix/sybase→postgresql/sql
  - 儲存：s3→blob、local-files→azure-storage、log-to-console
  - 安全：plaintext→keyvault、AWS-secrets→keyvault、憑證/加密→keyvault、MI for
    sql/mysql/postgresql/mongodb/cassandra/redis/servicebus/eventhub
  - 認證：on-prem→Entra ID；Email：javax→ACS；建置：ant/eclipse→maven

### 2. GitHub Copilot for Azure（`ms-azuretools.vscode-azure-github-copilot` v1.0.209）
聊天參與者：`@azure`。Language Model Tools：`azure_resources-query_azure_resource_graph`、
`azure_bicep-get_azure_verified_module`、`azure_auth-get/set_auth_context`、
`azure_dotnet_templates-*`。用於部署到 Azure、查資源、產生 Bicep、處理登入情境。
（另有 azure-mcp-server 提供大量 Azure 操作工具。）

## 對應策略

extension 負責「Java/Azure 領域知識」（assessment、配方、AppCAT），Superpowers 負責
「執行紀律」（TDD、review、worktree、收尾）。兩者組成：assessment → 計畫 → 自動執行。

## 低人工介入流程（只 4 個人為關卡）

```
using-superpowers（背景）
brainstorming ── 👤問答 / 👤approve / 👤review spec
writing-plans ── 👤選執行方式
using-git-worktrees（自動）
  subagent-driven-development（自動：每 task TDD→實作→verify→review）
finishing-a-development-branch ── 👤決定收尾
```

## 升級前後驗證

「執行」＝ `mvn clean verify`，升級前後皆需綠燈。AppCAT 報告與 git diff 為現代化證據。

## 排除範圍（YAGNI）

- 不需真實 broker；不在本 demo 自動佈建 Azure（保留為選配的 Copilot for Azure 步驟）。

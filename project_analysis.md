# Java Migration Copilot Samples — 專案分析

## 專案總覽

這個 repo 是 **Java 應用程式現代化 (App Modernization)** 的範例集合，搭配 GitHub Copilot App Modernization 擴充套件使用，包含 5 個獨立的 Java 子專案 + 2 個 .NET 範例。

---

## 1. `mi-sql-public-demo` — SQL Managed Identity 示範

| 項目 | 內容 |
|------|------|
| **語言/框架** | Java 17, 純 Maven 專案 (無 Spring Boot) |
| **目的** | 示範用 Managed Identity (MSI) 取代密碼連線 Azure SQL |
| **核心相依** | `mssql-jdbc 10.2.0` |
| **打包方式** | `maven-shade-plugin` → fat JAR，main class: `MainSQL` |

**需要 modernize 的部分：**
- 從硬編 connection string + clientId 的 `application.properties` 讀取設定，遷移到完整的 Azure Managed Identity 認證流程
- 目前已是「migration 後的示範結果」，本身就是目標狀態

**如何 run：**
```bash
cd mi-sql-public-demo
mvn clean package
java -jar target/demo-1.0-SNAPSHOT.jar
# 需要先設定 application.properties 中的 AZURE_SQLDB_CONNECTIONSTRING 與 AZURE_CLIENT_ID
```

---

## 2. `rabbitmq-sender` — RabbitMQ → Azure Service Bus 遷移示範

| 項目 | 內容 |
|------|------|
| **語言/框架** | Java 17, Spring Boot 3.3.0 |
| **目的** | 示範自訂 migration formula：RabbitMQ → Azure Service Bus |
| **核心相依** | `spring-cloud-azure-starter`, `spring-messaging-azure-servicebus`, Lombok, Jackson |
| **狀態** | 已遷移完成 (application.properties 指向 Azure Service Bus) |

**注意：** `pom.xml` 中 `spring-boot-docker-compose` 被註解掉，代表不再需要本地 RabbitMQ；code 裡直接使用 Azure Service Bus SDK。名稱雖為 rabbitmq-sender，但實際 code 已是遷移後的 Azure Service Bus 版本。

**需要 modernize 的部分：**
- 這是「遷移後」狀態的範例，重點是示範如何寫自訂 formula 讓 Copilot 套用同樣的遷移邏輯到其他專案

**如何 run：**
```bash
cd rabbitmq-sender
# 設定 application.properties:
#   spring.cloud.azure.servicebus.namespace=<your-namespace>
#   spring.cloud.azure.credential.client-id=<your-client-id>
mvn spring-boot:run
```

---

## 3. `asset-manager` — 完整端對端工作坊 (最複雜)

| 項目 | 內容 |
|------|------|
| **語言/框架** | **Java 8**, Spring Boot **2.7.18** (舊版) |
| **架構** | Multi-module Maven (父 POM + `web` 模組 + `worker` 模組) |
| **核心相依 (遷移前)** | AWS S3 SDK `2.25.13`, Spring AMQP (RabbitMQ), PostgreSQL, Lombok |
| **外部服務** | AWS S3, RabbitMQ, PostgreSQL |

**架構圖：**

```
User → WebApp → AWS S3 (原始圖片)
             → RabbitMQ → image-processing queue → Worker
             → PostgreSQL (metadata)
Worker → AWS S3 (thumbnail)
       → PostgreSQL (metadata)
```

**需要 modernize 的部分 (這是工作坊的主要學習目標)：**

| 遷移前 | 遷移後 |
|--------|--------|
| Java 8 | Java 21 |
| Spring Boot 2.7.x | Spring Boot 3.x |
| AWS S3 | Azure Blob Storage |
| RabbitMQ | Azure Service Bus |
| PostgreSQL (password auth) | Azure Database for PostgreSQL (Managed Identity) |
| 無容器化 | Docker / Azure Container Apps |
| 無 health check | Spring Actuator health endpoints |

**如何 run (本地)：**
```bash
cd asset-manager
# 需要: JDK 8, Maven 3.6+, Docker
scripts/startapp.sh    # macOS/Linux
scripts\startapp.cmd   # Windows
# 自動啟動 RabbitMQ + PostgreSQL Docker，使用本機 filesystem 代替 S3
# 停止: scripts/stopapp.sh 或 stopapp.cmd
```

**參考分支：**
- `main` — 遷移前原始狀態
- `workshop/java-upgrade` — 完成 assessment + Java 升級後
- `workshop/expected` — 完成所有服務遷移後
- `workshop/deployment-expected` — 完成容器化與部署後

---

## 4. `todo-web-api-use-oracle-db` — Oracle → PostgreSQL 遷移示範

| 項目 | 內容 |
|------|------|
| **語言/框架** | Java 17, Spring Boot 3.2.4 |
| **核心相依** | `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `ojdbc11` (Oracle driver), Lombok |
| **外部服務** | Oracle Database 21c XE (透過 Docker) |

**需要 modernize 的部分：**
- Oracle 專用 SQL 語法 (VARCHAR2、Oracle 函式) → 標準 ANSI SQL / PostgreSQL 語法
- `ojdbc11` driver → `postgresql` driver
- `application.properties` 中的 Oracle JDBC URL → Azure PostgreSQL Flexible Server URL
- Oracle 特有的 JPA dialect → PostgreSQL dialect
- schema.sql / data.sql 中的 Oracle 語法調整

**如何 run：**
```bash
# Step 1: 啟動 Oracle DB (首次初始化需 5-10 分鐘)
docker run -d --name oracle-xe \
  -p 1521:1521 \
  -e ORACLE_PWD=oracle \
  container-registry.oracle.com/database/express:latest

# 確認 DB 就緒
docker logs oracle-xe | grep "DATABASE IS READY TO USE"

# Step 2: 跑應用程式
cd todo-web-api-use-oracle-db
mvn clean spring-boot:run
# 預設跑在 http://localhost:8080

# 測試 API
curl -X GET http://localhost:8080/api/todos
```

---

## 5. `jakarta-ee/student-web-app` — Java EE → Jakarta EE 10 遷移示範

| 項目 | 內容 |
|------|------|
| **語言/框架** | Java 11, **Ant build system** (非 Maven), Open Liberty, Java EE 4.0 |
| **架構** | 混合架構：傳統 Servlet + Spring MVC 5.3.x |
| **核心相依** | MyBatis, Log4j, Jackson, MySQL Connector, Spring Framework 5.3.x |
| **外部服務** | MySQL 8.0 (Docker) |
| **Port** | 9080 (HTTP), 9443 (HTTPS) |

**需要 modernize 的部分 (兩階段)：**

**Phase 1 — 建構系統遷移：**
- `build.xml` (Ant) → `pom.xml` (Maven)

**Phase 2 — Framework 升級：**
- Java EE 4.0 (`javax.*` namespace) → Jakarta EE 10 (`jakarta.*` namespace)
- Spring Framework 5.3.x → Spring Framework 6.2.x
- `web.xml` 部署描述符 schema 版本更新
- Open Liberty server config 對應更新

**如何 run：**
```bash
cd jakarta-ee/student-web-app

# 方式一：Docker Compose (建議)
./setup-docker.sh        # macOS/Linux
setup-docker.bat         # Windows
docker-compose up
# 應用程式跑在 http://localhost:9080

# 方式二：手動 Ant build
ant war
# 部署 dist/OpenLibertyApp.war 到 Liberty server
```

---

## 專案相依性總覽

```
專案                          Java  Build   外部服務需求
──────────────────────────────────────────────────────────────
mi-sql-public-demo            17    Maven   Azure SQL DB + Managed Identity
rabbitmq-sender               17    Maven   Azure Service Bus + Managed Identity
asset-manager                 8     Maven   Docker (RabbitMQ + PostgreSQL) 本地開發
todo-web-api-use-oracle-db    17    Maven   Docker (Oracle Database 21c XE)
jakarta-ee/student-web-app    11    Ant     Docker (MySQL 8.0)
```

---

## 其他專案 (.NET)

| 專案 | 說明 |
|------|------|
| `ContosoUniversity` | ASP.NET MVC 5 (.NET Framework) 大學管理系統，含學生/課程/部門管理 |
| `Malshinon` | C# Console 應用程式，使用 SQL 資料庫連線 |

這兩個為 .NET/C# 專案，適用另一套 .NET 現代化流程，不在 Java migration 工作坊範疇內。

---

## 學習路徑建議

```
入門  →  mi-sql-public-demo      (了解 Managed Identity 概念)
       →  rabbitmq-sender         (了解自訂 migration formula)
進階  →  todo-web-api-use-oracle  (單一服務 DB 遷移)
完整  →  asset-manager            (端對端 Azure 遷移工作坊, ~2 小時)
特殊  →  jakarta-ee/student-app   (Ant → Maven + Java EE → Jakarta EE)
```

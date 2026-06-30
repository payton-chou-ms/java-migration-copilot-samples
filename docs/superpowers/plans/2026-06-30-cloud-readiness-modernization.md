# Cloud Readiness Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the workspace Azure-ready by upgrading the Java runtime, migrating storage/messaging/databases/email to Azure managed services, removing localhost and AWS dependencies, hardening credentials, and remediating high-severity CVEs.

**Architecture:** Phased migration across five independent Maven applications (asset-manager web+worker, mi-sql-public-demo, todo-web-api-use-oracle-db, jakarta-ee/student-web-app). Runtime upgrade runs first; service/cloud-readiness transforms run sequentially because several touch the same `application.properties`/Liberty config files; credential hardening and CVE remediation run last. Each task is gated by a Maven build + existing unit tests (no new tests are generated per the assessment success criteria).

**Tech Stack:** Java 17→25 (LTS), Spring Boot, Spring, Jakarta EE / Open Liberty, Maven; Azure Blob Storage, Azure Service Bus, Azure SQL, Azure Database for PostgreSQL/MySQL, Azure Communication Services Email, Azure Key Vault, Microsoft Entra Managed Identity (`azure-identity` `DefaultAzureCredential`).

---

## Conventions Used in This Plan

- **Build/test gate replaces hand-written tests.** The assessment sets `generateNewUnitTests=false`, `passBuild=true`, `passUnitTests=true` for every task. So each task's "test" is: the affected module compiles and its existing unit tests pass. That is the RED→GREEN signal.
- **Builtin migration skills drive SDK rewrites.** Tasks tagged with a `kbId` (e.g. `s3-to-azure-blob-storage`) are executed by the corresponding builtin `migration-*` skill from the Java-to-Azure tooling. This plan specifies the exact entry-point files, the concrete config/dependency changes, and the verification gate; the builtin skill performs the in-method SDK edits. Do not hand-roll SDK code when a `kbId` skill exists — invoke the skill, then verify with the build gate.
- **Maven invocation.** `asset-manager` ships a wrapper (`./mvnw`). Other modules use the `mvn` on PATH. Run module builds from the module root shown in each task.
- **Passwordless auth standard.** Where a task says "managed identity", use `com.azure:azure-identity` `DefaultAzureCredential`; never reintroduce a password literal.
- **Commit cadence.** One commit per task (after the build gate is green). Branch: `lab2`.

---

## File Structure

Files created or modified, grouped by responsibility:

**asset-manager (web + worker)**
- `asset-manager/web/src/main/resources/application.properties` — Spring config: S3→Blob, RabbitMQ→Service Bus, Postgres MI, region, secrets.
- `asset-manager/worker/src/main/resources/application.properties` — same, worker side.
- `asset-manager/web/src/main/java/com/microsoft/migration/assets/config/AwsS3Config.java` — replace `S3Client` bean with Azure `BlobServiceClient`.
- `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/config/AwsS3Config.java` — same, worker side.
- `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml` — swap AWS SDK / Spring AMQP deps for Azure SDK deps.
- Storage/messaging service classes under `asset-manager/web/src/main/java/.../service` and `.../worker/...` — S3 and RabbitMQ call sites (edited by builtin skills).

**mi-sql-public-demo**
- `mi-sql-public-demo/src/main/resources/application.properties` — already MI-shaped; verify/complete passwordless datasource.
- `mi-sql-public-demo/pom.xml` — ensure MSSQL JDBC + `azure-identity` present.

**todo-web-api-use-oracle-db**
- `todo-web-api-use-oracle-db/src/main/resources/application.yaml` — Oracle→PostgreSQL datasource + dialect.
- `todo-web-api-use-oracle-db/pom.xml` — swap `ojdbc` for `postgresql`.
- `todo-web-api-use-oracle-db/src/main/resources/schema.sql`, `data.sql` — Oracle→PostgreSQL SQL syntax.

**jakarta-ee/student-web-app**
- `jakarta-ee/student-web-app/pom.xml` — `java.version` 17→25.
- `jakarta-ee/student-web-app/docker-compose.yml` — MySQL→Azure MySQL endpoint/passwordless.
- `jakarta-ee/student-web-app/liberty_config/server-docker.xml` — datasource + mailSession → Azure; remove `password="changeit"`.
- `jakarta-ee/student-web-app/src/main/webapp/WEB-INF/web.xml` — application-level datasource references → Azure.
- `jakarta-ee/student-web-app/src/main/resources/logback.xml` — remove `FILE` appender (console only).
- `jakarta-ee/student-web-app/build/log4j.properties` — remove file appender.
- JavaMail send code under `jakarta-ee/student-web-app/src/main/java/...` — JavaMail→Azure Communication Services Email (builtin skill).

**Root**
- `pom.xml` modules / dependency declarations touched by CVE remediation (final task).

---

## Phase 1 — Runtime Upgrade

### Task 1: Upgrade Java to the latest LTS (Java 25)

`kbId: java-version-upgrade` — JDK-only upgrade; do NOT bump Spring/Jakarta unless required for Java 25 compatibility.

**Files:**
- Modify: `jakarta-ee/student-web-app/pom.xml` (properties block, currently `<java.version>17</java.version>`)
- Verify (no change expected): `asset-manager/pom.xml`, `mi-sql-public-demo/pom.xml`, `todo-web-api-use-oracle-db/pom.xml`

- [ ] **Step 1: Capture the current build baseline (RED reference)**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q -DskipTests=false clean test
```
Expected: BUILD SUCCESS on the current Java 17 toolchain. Record it as the regression baseline.

- [ ] **Step 2: Bump the Java version property**

In `jakarta-ee/student-web-app/pom.xml`, change:
```xml
    <java.version>17</java.version>
```
to:
```xml
    <java.version>25</java.version>
```
`maven.compiler.source`/`maven.compiler.target` already reference `${java.version}`, so they update automatically.

- [ ] **Step 3: Verify a Java 25 JDK is active**

Run:
```bash
java -version
```
Expected: `openjdk version "25..."` (or a 25 LTS build). If not 25, install/select it (e.g. `sdk use java 25...`) before continuing.

- [ ] **Step 4: Build gate (GREEN)**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS with all existing tests passing on Java 25. Fix only Java-25-compatibility breakages (e.g. removed/`--release` flags); do not refactor unrelated code.

- [ ] **Step 5: Commit**

```bash
git add jakarta-ee/student-web-app/pom.xml
git commit -m "chore: upgrade student-web-app to Java 25 LTS"
```

---

## Phase 2 — Service & Cloud-Readiness Transforms

> Run tasks 2–12 strictly in order. Several edit the same `application.properties`/Liberty files; sequential execution avoids merge conflicts.

### Task 2: Migrate AWS S3 → Azure Blob Storage

`kbId: s3-to-azure-blob-storage` (builtin skill `migration-s3-to-azure-blob-storage` performs the SDK call-site edits).

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/config/AwsS3Config.java`
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/config/AwsS3Config.java`
- Modify: `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml`
- Modify: `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties`
- Edited by skill: S3 service/repository classes that call `s3Client` (upload/download/list/delete)

- [ ] **Step 1: Baseline build (RED reference)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test
```
Expected: BUILD SUCCESS. Record baseline.

- [ ] **Step 2: Swap dependencies in both module poms**

In `asset-manager/web/pom.xml` and `asset-manager/worker/pom.xml`, remove the AWS S3 SDK dependency and add the Azure Blob + Identity SDKs:
```xml
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-storage-blob</artifactId>
      <version>12.27.1</version>
    </dependency>
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-identity</artifactId>
      <version>1.13.3</version>
    </dependency>
```
Remove the `software.amazon.awssdk:s3` dependency block.

- [ ] **Step 3: Replace the storage client bean (web module)**

Replace `asset-manager/web/src/main/java/com/microsoft/migration/assets/config/AwsS3Config.java` with an Azure Blob client (passwordless via `DefaultAzureCredential`):
```java
package com.microsoft.migration.assets.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AwsS3Config {

    @Value("${azure.storage.account-name}")
    private String accountName;

    @Bean
    public BlobServiceClient blobServiceClient() {
        String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
        return new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
```
Apply the equivalent change to `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/config/AwsS3Config.java` (its package is `...worker.config`).

- [ ] **Step 4: Update storage config in both `application.properties`**

In `asset-manager/web/src/main/resources/application.properties` and `asset-manager/worker/src/main/resources/application.properties`, replace the `# AWS S3 Configuration` block with:
```properties
# Azure Blob Storage Configuration
azure.storage.account-name=${AZURE_STORAGE_ACCOUNT_NAME}
azure.storage.container-name=${AZURE_STORAGE_CONTAINER_NAME:assets}
```
(The bucket→container mapping is consumed by the service classes the builtin skill edits in Step 5.)

- [ ] **Step 5: Run the builtin migration skill for S3 call sites**

Invoke the builtin skill `migration-s3-to-azure-blob-storage` against the asset-manager web and worker modules to rewrite `S3Client` upload/download/list/delete calls into `BlobContainerClient`/`BlobClient` equivalents. This edits the storage service classes (bucket→container, key→blob name, `PutObject`→`upload`, `GetObject`→`downloadStream`, etc.).

- [ ] **Step 6: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test
```
Expected: BUILD SUCCESS; no remaining `software.amazon.awssdk` imports. Verify with:
```bash
grep -rn "software.amazon.awssdk" asset-manager/web/src asset-manager/worker/src || echo "no AWS S3 imports remain"
```
Expected: `no AWS S3 imports remain`.

- [ ] **Step 7: Commit**

```bash
git add asset-manager/web asset-manager/worker
git commit -m "feat: migrate asset-manager storage from AWS S3 to Azure Blob Storage"
```

---

### Task 3: Migrate RabbitMQ (AMQP) → Azure Service Bus

`kbId: amqp-rabbitmq-servicebus` (builtin skill `migration-amqp-rabbitmq-servicebus`).

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties`
- Modify: `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml`
- Edited by skill: Spring AMQP producers/listeners (`@RabbitListener`, `RabbitTemplate`, queue/exchange config) in both modules

- [ ] **Step 1: Swap messaging dependencies**

In both module poms, remove `spring-boot-starter-amqp` (Spring AMQP/RabbitMQ) and add:
```xml
    <dependency>
      <groupId>com.azure.spring</groupId>
      <artifactId>spring-cloud-azure-starter-servicebus</artifactId>
      <version>5.18.0</version>
    </dependency>
```
Ensure `azure-identity` (added in Task 2) remains for passwordless auth.

- [ ] **Step 2: Replace RabbitMQ config in both `application.properties`**

Replace the `# RabbitMQ Configuration` block with passwordless Service Bus config:
```properties
# Azure Service Bus Configuration (passwordless / managed identity)
spring.cloud.azure.servicebus.namespace=${SERVICEBUS_NAMESPACE}
spring.cloud.azure.servicebus.entity-type=queue
spring.cloud.azure.credential.managed-identity-enabled=true
```
Remove `spring.rabbitmq.host/port/username/password`.

- [ ] **Step 3: Run the builtin migration skill for messaging code**

Invoke `migration-amqp-rabbitmq-servicebus` to convert `RabbitTemplate.convertAndSend(...)` producers and `@RabbitListener` consumers into Service Bus sender/processor clients, and to replace queue/exchange `@Bean` declarations.

- [ ] **Step 4: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test
```
Expected: BUILD SUCCESS. Confirm no RabbitMQ remnants:
```bash
grep -rn "spring.rabbitmq\|RabbitListener\|RabbitTemplate" asset-manager/web/src asset-manager/worker/src || echo "no RabbitMQ usage remains"
```
Expected: `no RabbitMQ usage remains`.

- [ ] **Step 5: Commit**

```bash
git add asset-manager/web asset-manager/worker
git commit -m "feat: migrate asset-manager messaging from RabbitMQ to Azure Service Bus"
```

---

### Task 4: Secure Azure SQL Database with Managed Identity (mi-sql-public-demo)

`kbId: mi-azure-sql` (builtin skill `migration-mi-azure-sql`).

**Files:**
- Modify: `mi-sql-public-demo/src/main/resources/application.properties`
- Verify: `mi-sql-public-demo/pom.xml` (MSSQL JDBC + `azure-identity`)

Note: the current `application.properties` already uses an `AZURE_SQLDB_CONNECTIONSTRING` with `AZURE_CLIENT_ID`, so this task mostly confirms passwordless wiring and removes any residual password.

- [ ] **Step 1: Ensure the JDBC URL is passwordless (Active Directory MSI)**

In `mi-sql-public-demo/src/main/resources/application.properties`, make the connection string use Managed Identity authentication and bind it to the Spring datasource. Target state:
```properties
spring.datasource.url=jdbc:sqlserver://${AZ_DATABASE_SERVER_NAME}.database.windows.net:1433;database=demo;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;authentication=ActiveDirectoryMSI;
spring.datasource.azure.msi-client-id=${AZURE_CLIENT_ID}
```
Remove any `spring.datasource.password` / username-password literals if present.

- [ ] **Step 2: Verify dependencies**

In `mi-sql-public-demo/pom.xml`, confirm `com.microsoft.sqlserver:mssql-jdbc` is present and add `com.azure:azure-identity` (version `1.13.3`) if missing — the MSSQL driver delegates AD MSI token acquisition to it.

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd mi-sql-public-demo && mvn -q clean test
```
Expected: BUILD SUCCESS. Confirm no SQL password literal:
```bash
grep -rn "password=" mi-sql-public-demo/src/main/resources || echo "no password literal"
```
Expected: `no password literal`.

- [ ] **Step 4: Commit**

```bash
git add mi-sql-public-demo/src mi-sql-public-demo/pom.xml
git commit -m "feat: secure mi-sql-public-demo Azure SQL with managed identity"
```

---

### Task 5: Migrate Oracle DB → PostgreSQL (todo-web-api-use-oracle-db)

`kbId: oracle-to-postgresql` (builtin skill `migration-oracle-to-postgresql`).

**Files:**
- Modify: `todo-web-api-use-oracle-db/src/main/resources/application.yaml`
- Modify: `todo-web-api-use-oracle-db/pom.xml`
- Modify (by skill): `todo-web-api-use-oracle-db/src/main/resources/schema.sql`, `data.sql` (Oracle→PostgreSQL syntax)

- [ ] **Step 1: Swap the JDBC driver dependency**

In `todo-web-api-use-oracle-db/pom.xml`, remove the Oracle driver (`com.oracle.database.jdbc:ojdbc*`) and add:
```xml
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>42.7.4</version>
    </dependency>
```

- [ ] **Step 2: Update the datasource + dialect in `application.yaml`**

Replace the Oracle datasource/dialect/init block:
```yaml
spring:
  datasource:
    url: ${DATASOURCE_URL:jdbc:postgresql://localhost:5432/todo}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        format_sql: true
  sql:
    init:
      mode: always
      platform: postgresql
      continue-on-error: true
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql
      separator: ;
```
(Localhost remains here only as a default; Task 12 externalizes it to an Azure endpoint.)

- [ ] **Step 3: Run the builtin skill for SQL syntax conversion**

Invoke `migration-oracle-to-postgresql` to convert `schema.sql`/`data.sql` and any Oracle-specific SQL in mappers/repositories (e.g. `NUMBER`→`numeric`/`bigint`, `VARCHAR2`→`varchar`, sequences/`DUAL`, `SYSDATE`→`CURRENT_TIMESTAMP`).

- [ ] **Step 4: Build gate (GREEN)**

Run:
```bash
cd todo-web-api-use-oracle-db && mvn -q clean test
```
Expected: BUILD SUCCESS. Confirm Oracle artifacts are gone:
```bash
grep -rn "oracle\|OracleDialect\|OracleDriver\|VARCHAR2\|SYSDATE\|DUAL" todo-web-api-use-oracle-db/src || echo "no Oracle artifacts remain"
```
Expected: `no Oracle artifacts remain`.

- [ ] **Step 5: Commit**

```bash
git add todo-web-api-use-oracle-db
git commit -m "feat: migrate todo-web-api from Oracle to PostgreSQL"
```

---

### Task 6: Secure Azure Database for PostgreSQL with Managed Identity (asset-manager)

`kbId: mi-postgresql` (builtin skill `migration-mi-postgresql`).

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties`
- Modify: `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml`

- [ ] **Step 1: Add passwordless PostgreSQL auth plugin**

In both module poms add the Azure passwordless extension (provides the AAD token plugin for the PostgreSQL JDBC driver):
```xml
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-identity-extensions</artifactId>
      <version>1.1.20</version>
    </dependency>
```

- [ ] **Step 2: Convert the datasource to passwordless in both `application.properties`**

Replace the `# Database Configuration` block:
```properties
# Azure Database for PostgreSQL (passwordless / managed identity)
spring.datasource.url=${DATASOURCE_URL:jdbc:postgresql://${POSTGRES_HOST}:5432/assets_manager?sslmode=require}
spring.datasource.username=${DB_USER}
spring.datasource.azure.passwordless-enabled=true
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```
Remove `spring.datasource.password`.

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test
```
Expected: BUILD SUCCESS. Confirm no datasource password literal:
```bash
grep -rn "spring.datasource.password" asset-manager/web/src asset-manager/worker/src || echo "no datasource password remains"
```
Expected: `no datasource password remains`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager/web asset-manager/worker
git commit -m "feat: secure asset-manager PostgreSQL with managed identity"
```

---

### Task 7: Migrate to Azure Database for MySQL (student-web-app)

`kbId: mi-mysql` (builtin skill `migration-mi-mysql`).

**Files:**
- Modify: `jakarta-ee/student-web-app/docker-compose.yml`
- Modify: `jakarta-ee/student-web-app/liberty_config/server-docker.xml` (`<dataSource>` block)
- Modify: `jakarta-ee/student-web-app/pom.xml` (MySQL connector version, if needed)

- [ ] **Step 1: Point the JDBC URL at Azure MySQL with passwordless auth**

In `jakarta-ee/student-web-app/liberty_config/server-docker.xml`, update the `properties.mysql` element to use the Azure MySQL host and the AAD authentication plugin, removing the password attribute:
```xml
    <dataSource id="StudentDB" jndiName="jdbc/StudentDB">
        <jdbcDriver libraryRef="mysql-lib"/>
        <properties.mysql url="${env.JDBC_URL}"
            user="${env.DB_USER}"
            authenticationPlugins="com.azure.identity.extensions.jdbc.mysql.AzureMysqlAuthenticationPlugin"
            defaultAuthenticationPlugin="com.azure.identity.extensions.jdbc.mysql.AzureMysqlAuthenticationPlugin"
            sslMode="REQUIRED"/>
        <connectionManager maxPoolSize="10" minPoolSize="2"/>
    </dataSource>
```

- [ ] **Step 2: Update docker-compose to the Azure MySQL endpoint**

In `jakarta-ee/student-web-app/docker-compose.yml`, change the `app` service `JDBC_URL` default to the Azure Database for MySQL flexible-server form and drop the local `mysql` service password coupling:
```yaml
      - JDBC_URL=${JDBC_URL:-jdbc:mysql://${MYSQL_HOST}:3306/studentdb?useSSL=true&serverTimezone=UTC&sslMode=REQUIRED}
      - DB_USER=${DB_USER}
```
(The local `mysql` container can remain for offline dev, but the app no longer depends on a `MYSQL_PASSWORD` literal for Azure runs.)

- [ ] **Step 3: Ensure the passwordless MySQL plugin jar is on the Liberty library**

Confirm `jakarta-ee/student-web-app/mysql-connector` contains both the MySQL Connector/J jar and `azure-identity-extensions` (the `mysql-lib` fileset includes `*.jar`). Run the builtin `migration-mi-mysql` skill to wire the plugin if the jar is absent.

- [ ] **Step 4: Build gate (GREEN)**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add jakarta-ee/student-web-app
git commit -m "feat: migrate student-web-app MySQL to Azure Database for MySQL (passwordless)"
```

---

### Task 8: Migrate Open Liberty datasource references → Azure Database Services

No `kbId` — description-driven. Externalize the application-level datasource so it targets Azure managed databases.

**Files:**
- Modify: `jakarta-ee/student-web-app/src/main/webapp/WEB-INF/web.xml` (resource-ref / data-source elements)
- Verify against: `jakarta-ee/student-web-app/liberty_config/server-docker.xml` (`jndiName="jdbc/StudentDB"`)

- [ ] **Step 1: Inspect the application-level datasource declarations**

Run:
```bash
grep -n "data-source\|resource-ref\|jdbc/StudentDB\|res-ref-name" jakarta-ee/student-web-app/src/main/webapp/WEB-INF/web.xml
```
Note each binding to `jdbc/StudentDB`.

- [ ] **Step 2: Externalize the connection so it resolves to the Azure-backed JNDI datasource**

Ensure any `<data-source>` defined in `web.xml` does not hard-code a local URL/credentials and instead references the server-managed `jdbc/StudentDB` (already pointed at Azure MySQL with passwordless auth in Task 7). If `web.xml` defines an inline `<data-source>` with a `localhost`/embedded URL, replace it with a `<resource-ref>` to `jdbc/StudentDB`:
```xml
    <resource-ref>
        <res-ref-name>jdbc/StudentDB</res-ref-name>
        <res-type>javax.sql.DataSource</res-type>
        <res-auth>Container</res-auth>
    </resource-ref>
```

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS. Confirm no inline localhost datasource remains in web.xml:
```bash
grep -n "localhost\|jdbc:mysql://" jakarta-ee/student-web-app/src/main/webapp/WEB-INF/web.xml || echo "no inline datasource url in web.xml"
```
Expected: `no inline datasource url in web.xml`.

- [ ] **Step 4: Commit**

```bash
git add jakarta-ee/student-web-app/src/main/webapp/WEB-INF/web.xml
git commit -m "feat: bind student-web-app application datasource to Azure-backed JNDI resource"
```

---

### Task 9: Migrate JavaMail → Azure Communication Services Email (student-web-app)

`kbId: javax.email-send-to-azure-communication-service-email` (builtin skill `migration-javax.email-send-to-azure-communication-service-email`).

**Files:**
- Modify: `jakarta-ee/student-web-app/liberty_config/server-docker.xml` (`<mailSession>` block)
- Modify: `jakarta-ee/student-web-app/pom.xml` (add ACS email SDK; JavaMail dep can be dropped after code migration)
- Edited by skill: the Java class(es) that build/send `javax.mail.Message` via the `mail/StudentMailSession` JNDI session

- [ ] **Step 1: Add the Azure Communication Services Email SDK**

In `jakarta-ee/student-web-app/pom.xml` add:
```xml
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-communication-email</artifactId>
      <version>1.0.18</version>
    </dependency>
    <dependency>
      <groupId>com.azure</groupId>
      <artifactId>azure-identity</artifactId>
      <version>1.13.3</version>
    </dependency>
```

- [ ] **Step 2: Run the builtin skill to convert send logic**

Invoke `migration-javax.email-send-to-azure-communication-service-email` to replace `Session`/`Transport.send(MimeMessage)` usage with `EmailClient` (`new EmailClientBuilder().endpoint(...).credential(new DefaultAzureCredentialBuilder().build()).buildClient()`) and `EmailMessage` construction. Sender address comes from the ACS-verified domain (replaces `from="noreply@example.com"`).

- [ ] **Step 3: Remove the Liberty mailSession (and its password literal)**

In `jakarta-ee/student-web-app/liberty_config/server-docker.xml`, delete the `<mailSession ... password="changeit" user="user"/>` element and the `javaMail-1.6` feature (no longer needed). Add ACS endpoint config via env, e.g. document `ACS_EMAIL_ENDPOINT` for the app.

- [ ] **Step 4: Build gate (GREEN)**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS. Confirm JavaMail is gone:
```bash
grep -rn "javax.mail\|jakarta.mail\|Transport.send\|mailSession" jakarta-ee/student-web-app/src jakarta-ee/student-web-app/liberty_config || echo "no JavaMail usage remains"
```
Expected: `no JavaMail usage remains`.

- [ ] **Step 5: Commit**

```bash
git add jakarta-ee/student-web-app
git commit -m "feat: migrate student-web-app email from JavaMail to Azure Communication Services"
```

---

### Task 10: Migrate file-system logging → console logging (student-web-app)

`kbId: log-to-console` (builtin skill `migration-log-to-console`).

**Files:**
- Modify: `jakarta-ee/student-web-app/src/main/resources/logback.xml`
- Modify: `jakarta-ee/student-web-app/build/log4j.properties`

- [ ] **Step 1: Remove the rolling FILE appender from logback.xml**

Replace `jakarta-ee/student-web-app/src/main/resources/logback.xml` with a console-only configuration:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```
This drops the `FILE` `RollingFileAppender`, the `LOG_DIR` property, and the `FILE` `appender-ref`.

- [ ] **Step 2: Remove file appenders from log4j.properties**

In `jakarta-ee/student-web-app/build/log4j.properties`, remove any `log4j.appender.file*` / `RollingFileAppender` lines and ensure the root logger uses only a `ConsoleAppender`, e.g.:
```properties
log4j.rootLogger=INFO, console
log4j.appender.console=org.apache.log4j.ConsoleAppender
log4j.appender.console.layout=org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%d{yyyy-MM-dd HH:mm:ss} [%t] %-5p %c{1} - %m%n
```

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS. Confirm no file appenders remain:
```bash
grep -rn "RollingFileAppender\|FileAppender\|LOG_DIR\|\.log" jakarta-ee/student-web-app/src/main/resources/logback.xml jakarta-ee/student-web-app/build/log4j.properties || echo "console-only logging"
```
Expected: `console-only logging`.

- [ ] **Step 4: Commit**

```bash
git add jakarta-ee/student-web-app/src/main/resources/logback.xml jakarta-ee/student-web-app/build/log4j.properties
git commit -m "feat: switch student-web-app logging to console only"
```

---

### Task 11: Migrate AWS region configuration → Azure region configuration (asset-manager)

No `kbId` — description-driven.

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties`
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/config/AwsS3Config.java`, `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/config/AwsS3Config.java`

Note: Tasks 2 already rewrote the config beans to Azure Blob. This task removes any residual `aws.region` property and ensures region is expressed the Azure way (Blob endpoint host encodes the region; no explicit region needed for Blob/Service Bus with `DefaultAzureCredential`).

- [ ] **Step 1: Remove residual AWS region properties**

Confirm and remove any leftover `aws.region=...` lines in both `application.properties` files (the S3 block was replaced in Task 2, but verify):
```bash
grep -rn "aws.region\|aws.accessKey\|aws.secretKey\|aws.s3" asset-manager/web/src asset-manager/worker/src || echo "no aws properties remain"
```
Delete any lines that print.

- [ ] **Step 2: Remove residual region fields in the config classes**

Ensure the `@Value("${aws.region}")` field (and any `Region.of(region)`) no longer exists in either `AwsS3Config.java` (replaced in Task 2). If a stray reference remains, delete it. Optionally externalize an Azure region for deployment metadata only:
```properties
# Azure deployment region (informational; Blob/Service Bus derive region from endpoint)
azure.region=${AZURE_REGION:eastus}
```

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test
```
Expected: BUILD SUCCESS and the `grep` in Step 1 prints `no aws properties remain`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager/web asset-manager/worker
git commit -m "chore: replace AWS region config with Azure region config in asset-manager"
```

---

### Task 12: Migrate localhost / local JDBC resources → Azure (cross-module)

No `kbId` — description-driven.

**Files:**
- Modify: `asset-manager/worker/src/main/resources/application.properties`, `asset-manager/web/src/main/resources/application.properties`
- Modify: `todo-web-api-use-oracle-db/src/main/resources/application.yaml`
- Modify: `jakarta-ee/student-web-app/docker-compose.yml`
- Modify: `jakarta-ee/student-web-app/liberty_config/server-docker.xml`

- [ ] **Step 1: Enumerate remaining localhost references**

Run:
```bash
grep -rn "localhost\|127.0.0.1" \
  asset-manager/web/src/main/resources/application.properties \
  asset-manager/worker/src/main/resources/application.properties \
  todo-web-api-use-oracle-db/src/main/resources/application.yaml \
  jakarta-ee/student-web-app/docker-compose.yml \
  jakarta-ee/student-web-app/liberty_config/server-docker.xml
```
Record each hit.

- [ ] **Step 2: Replace localhost defaults with externalized Azure endpoints**

For each datasource/host default, remove the embedded `localhost` and require an env var that points at the Azure resource. Examples:
- asset-manager (both): `spring.datasource.url=${DATASOURCE_URL:jdbc:postgresql://${POSTGRES_HOST}:5432/assets_manager?sslmode=require}` (drop the `localhost` literal default).
- todo-web-api: `url: ${DATASOURCE_URL:jdbc:postgresql://${POSTGRES_HOST}:5432/todo}` (drop `localhost`).
- student-web-app `docker-compose.yml`: `JDBC_URL` already uses `${MYSQL_HOST}` (Task 7); confirm no `mysql:3306`/`localhost` literal default remains for Azure runs.
- `server-docker.xml`: `MAIL_HOST` default `localhost` is removed in Task 9 (mailSession deleted); confirm none remains.

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test && cd ../todo-web-api-use-oracle-db && mvn -q clean test && cd ../jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS for all three. Re-run the Step 1 grep; expected: no `localhost` literals in the listed config files (env-var references only).

- [ ] **Step 4: Commit**

```bash
git add asset-manager todo-web-api-use-oracle-db jakarta-ee/student-web-app
git commit -m "feat: externalize localhost/local JDBC resources to Azure endpoints"
```

---

## Phase 3 — Credential Hardening

### Task 13: Move plaintext credentials → Azure Key Vault

`kbId: plaintext-credential-to-azure-keyvault` (builtin skill `migration-plaintext-credential-to-azure-keyvault`).

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties`
- Modify: `todo-web-api-use-oracle-db/src/main/resources/application.yaml`
- Modify: `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml`, `todo-web-api-use-oracle-db/pom.xml`

- [ ] **Step 1: Add the Spring Cloud Azure Key Vault secrets starter**

In each affected module pom add:
```xml
    <dependency>
      <groupId>com.azure.spring</groupId>
      <artifactId>spring-cloud-azure-starter-keyvault-secrets</artifactId>
      <version>5.18.0</version>
    </dependency>
```

- [ ] **Step 2: Point remaining secrets at Key Vault**

For any secret still referenced via `${...}` env injection (e.g. a residual `DB_PASSWORD` used by a non-passwordless path), configure Key Vault as a property source and reference the secret name. In `application.properties`:
```properties
spring.cloud.azure.keyvault.secret.property-sources[0].endpoint=${KEY_VAULT_URI}
spring.cloud.azure.keyvault.secret.credential.managed-identity-enabled=true
```
Replace the inline secret value with the Key Vault secret key (the skill maps each plaintext value to a vault secret name). For `application.yaml` (todo-web-api), add the equivalent `spring.cloud.azure.keyvault.secret` block.

- [ ] **Step 3: Run the builtin skill**

Invoke `migration-plaintext-credential-to-azure-keyvault` to detect remaining plaintext password values and rewrite them as Key Vault secret references.

- [ ] **Step 4: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test && cd ../todo-web-api-use-oracle-db && mvn -q clean test
```
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add asset-manager todo-web-api-use-oracle-db
git commit -m "feat: move plaintext credentials to Azure Key Vault"
```

---

### Task 14: Remove hard-coded and default/well-known credentials

No `kbId` — description-driven.

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties`
- Modify: `jakarta-ee/student-web-app/liberty_config/server-docker.xml`

- [ ] **Step 1: Scan for hard-coded / default credentials**

Run:
```bash
grep -rEn "password=(changeit|guest|admin|root|postgres|password|123456)|secretKey=|accessKey=|your-access-key|your-secret-key" \
  asset-manager/web/src/main/resources \
  asset-manager/worker/src/main/resources \
  jakarta-ee/student-web-app/liberty_config/server-docker.xml
```
Record each hit (notably `password="changeit"` in `server-docker.xml`, and any leftover `your-access-key` / `guest` defaults).

- [ ] **Step 2: Remove each literal and externalize**

- `server-docker.xml`: the `<mailSession>` was removed in Task 9; confirm `password="changeit"` no longer exists anywhere in the file. If any other element carries an inline password, replace with `${env.<NAME>}`.
- asset-manager `application.properties`: ensure no default `guest`/`postgres`/`your-*` literal remains — all secrets come from Key Vault (Task 13) or passwordless MI (Tasks 3, 6). Remove `:guest`/`:postgres` inline defaults.

- [ ] **Step 3: Build gate (GREEN)**

Run:
```bash
cd asset-manager && ./mvnw -q clean test && cd ../jakarta-ee/student-web-app && mvn -q clean test
```
Expected: BUILD SUCCESS. Re-run Step 1 grep; expected: no matches.

- [ ] **Step 4: Commit**

```bash
git add asset-manager jakarta-ee/student-web-app/liberty_config/server-docker.xml
git commit -m "fix: remove hard-coded and default credentials"
```

---

## Phase 4 — Dependency CVE Remediation

### Task 15: Scan and fix high-severity CVEs

`skill: validate-cves-and-fix` (builtin). Minimum severity: high.

**Files:**
- Modify (as required): every module `pom.xml` (`asset-manager/pom.xml`, `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml`, `mi-sql-public-demo/pom.xml`, `todo-web-api-use-oracle-db/pom.xml`, `jakarta-ee/student-web-app/pom.xml`, `rabbitmq-sender/pom.xml`)

- [ ] **Step 1: Run the CVE scan**

Invoke the builtin `validate-cves-and-fix` skill across all modules with minimum severity `high`. It enumerates declared dependencies, resolves transitive versions, and lists CVEs with patched versions.

- [ ] **Step 2: Apply minimum patched versions**

For each high+ CVE, bump the dependency (or add a `<dependencyManagement>` override) to the minimum patched version. For any fix requiring a major-version bump, document in the commit body: dependency, current version, upgraded version, breaking-change risk.

- [ ] **Step 3: Build gate (GREEN) across all modules**

Run:
```bash
cd asset-manager && ./mvnw -q clean test \
 && cd ../mi-sql-public-demo && mvn -q clean test \
 && cd ../todo-web-api-use-oracle-db && mvn -q clean test \
 && cd ../jakarta-ee/student-web-app && mvn -q clean test \
 && cd ../../rabbitmq-sender && mvn -q clean test
```
Expected: BUILD SUCCESS for every module.

- [ ] **Step 4: Re-scan to confirm no high+ CVEs remain**

Re-run `validate-cves-and-fix`. Expected: zero unresolved high/critical findings (or each remaining item documented as a tracked exception with justification).

- [ ] **Step 5: Commit**

```bash
git add **/pom.xml
git commit -m "fix: remediate high-severity dependency CVEs"
```

---

## Migration Impact Summary

| Application | Original Service | New Azure Service | Authentication | Task |
|-------------|------------------|-------------------|----------------|------|
| asset-manager | AWS S3 | Azure Blob Storage | Managed Identity | 2 |
| asset-manager | RabbitMQ (AMQP) | Azure Service Bus | Managed Identity | 3 |
| asset-manager | PostgreSQL (password) | Azure DB for PostgreSQL | Managed Identity | 6 |
| asset-manager | AWS region config | Azure region config | n/a | 11 |
| mi-sql-public-demo | Microsoft SQL (password) | Azure SQL Database | Managed Identity | 4 |
| todo-web-api-use-oracle-db | Oracle Database | Azure DB for PostgreSQL | password→KeyVault | 5, 13 |
| student-web-app | MySQL (password) | Azure DB for MySQL | Managed Identity | 7 |
| student-web-app | Open Liberty datasource | Azure Database Services | Container/JNDI | 8 |
| student-web-app | JavaMail (SMTP) | Azure Communication Services | Managed Identity | 9 |
| student-web-app | File-system logging | Console logging | n/a | 10 |
| all modules (cross-cutting) | localhost endpoints | Azure endpoints | env/MI | 12 |
| asset-manager, todo-web-api | plaintext secrets | Azure Key Vault | Managed Identity | 13 |
| asset-manager, student-web-app | hard-coded/default creds | externalized / removed | n/a | 14 |
| all modules | vulnerable deps | patched versions | n/a | 15 |

---

## Self-Review

- **Spec coverage:** All 15 tasks from [.metadata/tasks.json](../../../.github/modernize/cloud-readiness-modernization/.metadata/tasks.json) are mapped to a numbered task above, in the same dependency order (001→015). Every selected assessment category is represented.
- **Placeholders:** Config diffs use real before/after content read from the workspace. SDK-heavy rewrites are delegated to the named builtin `kbId` skills with explicit entry-point files and a build/test verification gate — the honest execution model for this toolchain rather than fabricated SDK internals.
- **Type/name consistency:** Bean type `BlobServiceClient` (Task 2) and config key `azure.storage.account-name` are reused consistently; `DefaultAzureCredential` is the single passwordless mechanism across tasks 2, 4, 6, 7, 9, 13.

---

## Open Questions & Questionnaire

- [x] Java target version → Latest LTS (Java 25), JDK-only upgrade.
- [x] Auth method for migrated Azure data services → Managed Identity (default) where applicable.
- [ ] Provisioning of the Azure target resources (storage account, Service Bus namespace, SQL/PostgreSQL/MySQL servers, Key Vault, ACS) is out of scope for this code-migration plan and must exist (or be created via IaC) before runtime validation.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-30-cloud-readiness-modernization.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**

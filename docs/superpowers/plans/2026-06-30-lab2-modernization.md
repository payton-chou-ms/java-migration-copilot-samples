# Lab2 Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the workspace's Java modules to the latest LTS (Java 25), standardize the build (Ant → Maven), move localhost resources to Azure-ready externalized config, and remediate the assessment's CWE and CVE findings so every module builds and tests pass before deployment.

**Architecture:** Phased, sequential migration. Phase 1 upgrades the runtime (and the minimum framework versions required for Java 25 compatibility). Phase 2 standardizes build tooling and externalizes cloud resources. Phase 3 remediates security findings (CWE) one class at a time to avoid merge conflicts on overlapping files. Phase 4 patches dependency CVEs. Each task ends with a build/test gate and a commit.

**Tech Stack:** Java 25 (LTS), Maven, Spring Boot 3.5.x (where compatibility requires the bump), Jakarta EE 10 / Open Liberty, MyBatis, RabbitMQ/Azure Service Bus, PostgreSQL/Oracle/MySQL JDBC, Lombok, Thymeleaf, JUnit 5.

---

## Scope Check (read before starting)

This plan covers **multiple independent subsystems** that build and ship separately:

| Subsystem | Path | Build | Runtime today |
|-----------|------|-------|---------------|
| asset-manager (web + worker) | `asset-manager/` | Maven (multi-module) | Java 8, Spring Boot 2.7.18 |
| todo-web-api | `todo-web-api-use-oracle-db/` | Maven | Java 17, Spring Boot 3.2.4 |
| rabbitmq-sender | `rabbitmq-sender/` | Maven | Java 17, Spring Boot 3.3.0 |
| mi-sql-public-demo | `mi-sql-public-demo/` | Maven | (verify in Task 1) |
| student-web-app | `jakarta-ee/student-web-app/` | **Ant** | Java 11, Jakarta EE on Open Liberty |
| Malshinon (C#) | `Malshinon/` | MSBuild | .NET — credential findings only |

**Recommendation:** These are independent deployables. If you have the option, execute each subsystem as its own plan/PR. This document keeps them together because the modernization workflow (`.github/modernize/lab2-modernization`) treats them as one sequential pipeline with cross-cutting security passes. **Each phase still produces independently buildable software** — do not start Phase 2 until Phase 1 builds green.

**Compatibility note (important):** The source tasks say "JDK-only upgrade unless required for compatibility." Java 25 *is* a compatibility trigger:
- Spring Boot 2.7.x (asset-manager) does **not** run on Java 25 and is EOL → requires Spring Boot 3.x, which forces `javax.*` → `jakarta.*`. Treated as required and included in Task 1.
- Spring Boot 3.2/3.3 (todo, rabbitmq) predate Java 25 support → bump to the latest Spring Boot 3.5.x patch. Treated as required and included in Task 1.

---

## File Structure

Files created or modified, grouped by responsibility. Decomposition decisions are locked in here.

**Phase 1 — runtime/build descriptors**
- `asset-manager/pom.xml` — parent: `java.version` 8 → 25, Spring Boot parent 2.7.18 → 3.5.x
- `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml` — inherit; fix `javax.*` deps if any
- `asset-manager/**/src/main/java/**` — `javax.annotation.PostConstruct` → `jakarta.annotation.PostConstruct`
- `todo-web-api-use-oracle-db/pom.xml` — `java.version` 17 → 25, Spring Boot 3.2.4 → 3.5.x
- `rabbitmq-sender/pom.xml` — `java.version` 17 → 25, Spring Boot 3.3.0 → 3.5.x
- `mi-sql-public-demo/pom.xml` — `java.version` → 25 (verify framework)

**Phase 2 — build tool + cloud readiness**
- `jakarta-ee/student-web-app/pom.xml` — **new**, replaces `build.xml`
- `jakarta-ee/student-web-app/src/main/java/**`, `src/main/webapp/**`, `src/main/resources/**` — **new** Maven layout (moved from `src/`, `WebContent/`, `resources/`)
- `asset-manager/web/src/main/resources/application.properties`, `asset-manager/worker/src/main/resources/application.properties` — externalize datasource/RabbitMQ to env placeholders
- `todo-web-api-use-oracle-db/src/main/resources/application.yaml` — externalize datasource to env placeholders
- `jakarta-ee/student-web-app/docker-compose.yml`, `liberty_config/server-docker.xml` — externalize DB to env

**Phase 3 — security (one CWE per task)** — files listed per task below.

**Phase 4 — CVE**
- `asset-manager/web/pom.xml`, `asset-manager/worker/pom.xml`, `todo-web-api-use-oracle-db/pom.xml`, `rabbitmq-sender/pom.xml` — pin patched dependency versions.

---

## Conventions for every task

- **Branch per task:** `git switch -c task-NNN-short-name` off the previous task's tip.
- **Build gate** is the test. The source `tasks.json` sets `generateNewUnitTests:false`; these modules largely lack unit tests, so the **pass condition is a clean `mvn -q clean verify`** (or the module's equivalent) plus any targeted check named in the task. Where a module already has tests, they must still pass.
- **Run Maven from the module root.** Use the wrapper if present (`./mvnw`), else `mvn`.
- **Commit** at the end of every task with the exact message shown.

---

# Phase 1 — Runtime Upgrade

### Task 001: Upgrade all Java modules to Java 25

**Files:**
- Modify: `asset-manager/pom.xml`
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/LocalFileProcessingService.java`
- Modify: `todo-web-api-use-oracle-db/pom.xml`
- Modify: `rabbitmq-sender/pom.xml`
- Modify: `mi-sql-public-demo/pom.xml`

- [ ] **Step 1: Confirm a Java 25 JDK is active**

Run: `java -version`
Expected: `openjdk version "25"` (or a vendor build of 25). If not, install Temurin/Microsoft Build of OpenJDK 25 and re-run before continuing.

- [ ] **Step 2: Capture the current build baseline**

Run (from repo root):
```bash
( cd asset-manager && mvn -q -DskipTests clean package ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q -DskipTests clean package ) ; echo "todo exit=$?"
( cd rabbitmq-sender && mvn -q -DskipTests clean package ) ; echo "rabbitmq exit=$?"
( cd mi-sql-public-demo && mvn -q -DskipTests clean package ) ; echo "mi-sql exit=$?"
```
Expected: asset-manager FAILS or warns on Java 25 (Spring Boot 2.7.18 incompatible); note the others' status. This is your before-state.

- [ ] **Step 3: Bump the asset-manager parent (Java + Spring Boot)**

In `asset-manager/pom.xml`, change the Spring Boot parent and Java version:
```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.4</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>

    <groupId>com.microsoft.migration</groupId>
    <artifactId>assets-manager-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>25</java.version>
    </properties>
```

- [ ] **Step 4: Migrate `javax.annotation` → `jakarta.annotation` in asset-manager**

Spring Boot 3 requires Jakarta namespaces. In `LocalFileStorageService.java` and `LocalFileProcessingService.java` replace the import:
```java
// before
import javax.annotation.PostConstruct;
// after
import jakarta.annotation.PostConstruct;
```
Then scan for any other `javax.*` (except `javax.imageio.*`, `javax.sql.*`, `javax.naming.*`, which remain under `javax`):
Run: `grep -rn "import javax\." asset-manager/web/src asset-manager/worker/src`
Replace only persistence/servlet/annotation/validation namespaces (`javax.persistence`→`jakarta.persistence`, `javax.servlet`→`jakarta.servlet`, `javax.validation`→`jakarta.validation`, `javax.annotation`→`jakarta.annotation`). Leave `javax.imageio`, `javax.naming`, `javax.sql` unchanged.

- [ ] **Step 5: Bump todo-web-api to Java 25**

In `todo-web-api-use-oracle-db/pom.xml`:
```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.4</version>
        <relativePath/>
    </parent>
```
```xml
    <properties>
        <java.version>25</java.version>
    </properties>
```

- [ ] **Step 6: Bump rabbitmq-sender to Java 25**

In `rabbitmq-sender/pom.xml`:
```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.4</version>
        <relativePath/>
    </parent>
```
```xml
    <properties>
        <java.version>25</java.version>
    </properties>
```
Note: `spring-cloud-azure-dependencies` 5.14.0 is compatible with Spring Boot 3.3; if `mvn` reports a version-alignment error, bump it to the latest 5.22.x in the same `dependencyManagement` block and re-run.

- [ ] **Step 7: Bump mi-sql-public-demo to Java 25**

Open `mi-sql-public-demo/pom.xml`. If it uses the Spring Boot parent, bump it to `3.5.4` like the others. Set:
```xml
    <properties>
        <java.version>25</java.version>
    </properties>
```
If it sets `maven.compiler.source/target` instead, change both to `25`.

- [ ] **Step 8: Build every module and fix fallout**

Run:
```bash
( cd asset-manager && mvn -q clean verify ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q clean verify ) ; echo "todo exit=$?"
( cd rabbitmq-sender && mvn -q clean verify ) ; echo "rabbitmq exit=$?"
( cd mi-sql-public-demo && mvn -q clean verify ) ; echo "mi-sql exit=$?"
```
Expected: all `exit=0`. Typical fixes if not: remaining `javax.*` imports (repeat Step 4), or a transitive dependency that needs the Spring Boot 3 coordinate. Resolve until all four are green.

- [ ] **Step 9: Commit**

```bash
git add asset-manager todo-web-api-use-oracle-db rabbitmq-sender mi-sql-public-demo
git commit -m "feat: upgrade Java modules to Java 25 (LTS)"
```

---

# Phase 2 — Build & Cloud-Readiness Transforms

### Task 002: Migrate student-web-app from Ant to Maven

**Files:**
- Create: `jakarta-ee/student-web-app/pom.xml`
- Move: `jakarta-ee/student-web-app/src/**` → `jakarta-ee/student-web-app/src/main/java/**`
- Move: `jakarta-ee/student-web-app/WebContent/**` → `jakarta-ee/student-web-app/src/main/webapp/**`
- Move: `jakarta-ee/student-web-app/resources/**` → `jakarta-ee/student-web-app/src/main/resources/**`
- Delete (after green build): `jakarta-ee/student-web-app/build.xml`, `build.properties`

> **Depends on:** Task 001.

- [ ] **Step 1: Inventory the Ant build inputs**

Run:
```bash
sed -n '1,200p' jakarta-ee/student-web-app/build.xml
ls jakarta-ee/student-web-app/WebContent/WEB-INF/lib/*/ 2>/dev/null
```
Note the four lib groups the WAR bundles: `log4j`, `jackson`, `mybatis`, `spring`. These become Maven dependencies. The compile uses `source/target=11`; the WAR is `OpenLibertyApp.war` built from `WebContent/WEB-INF/web.xml`.

- [ ] **Step 2: Create the Maven standard directory layout**

Run:
```bash
cd jakarta-ee/student-web-app
mkdir -p src/main/java src/main/resources src/main/webapp
git mv src/org src/main/java/org
git mv resources/* src/main/resources/
git mv WebContent/* src/main/webapp/
cd -
```
Result: Java sources under `src/main/java/org/...`, `sql-map-config.xml`/`log4j.properties` under `src/main/resources/`, JSPs and `WEB-INF/` under `src/main/webapp/`.

- [ ] **Step 3: Create `pom.xml`**

Create `jakarta-ee/student-web-app/pom.xml` (WAR packaging, Java 25, the four bundled lib groups expressed as dependencies):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.sample.azure</groupId>
    <artifactId>student-web-app</artifactId>
    <version>1.0.0</version>
    <packaging>war</packaging>
    <name>student-web-app</name>

    <properties>
        <maven.compiler.release>25</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <failOnMissingWebXml>false</failOnMissingWebXml>
    </properties>

    <dependencies>
        <!-- Servlet/JSP API provided by Open Liberty -->
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>5.0.0</version>
            <scope>provided</scope>
        </dependency>
        <!-- iBATIS / MyBatis (legacy SqlMapClient API used by the servlets) -->
        <dependency>
            <groupId>org.apache.ibatis</groupId>
            <artifactId>ibatis-sqlmap</artifactId>
            <version>2.3.0</version>
        </dependency>
        <!-- Logging -->
        <dependency>
            <groupId>log4j</groupId>
            <artifactId>log4j</artifactId>
            <version>1.2.17</version>
        </dependency>
        <!-- JSON -->
        <dependency>
            <groupId>org.codehaus.jackson</groupId>
            <artifactId>jackson-mapper-asl</artifactId>
            <version>1.9.13</version>
        </dependency>
        <!-- MySQL driver (matches docker-compose) -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>9.0.0</version>
        </dependency>
    </dependencies>

    <build>
        <finalName>OpenLibertyApp</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
            </plugin>
        </plugins>
    </build>
</project>
```
> If iBATIS 2.x is not resolvable from your registry, keep the existing `WEB-INF/lib/mybatis/*.jar` as a `system`-scoped dependency or install it to the local repo with `mvn install:install-file`. Tasks 004/009 later replace this legacy API, which removes the problem permanently.

- [ ] **Step 4: Build the WAR with Maven**

Run:
```bash
cd jakarta-ee/student-web-app && mvn -q clean package ; echo "exit=$?" ; cd -
```
Expected: `exit=0` and `target/OpenLibertyApp.war` exists (same artifact name the Dockerfile/Liberty config expects).
Verify: `ls jakarta-ee/student-web-app/target/OpenLibertyApp.war`

- [ ] **Step 5: Point the Dockerfile at the Maven output (if needed)**

Run: `grep -n "dist/\|\.war" jakarta-ee/student-web-app/Dockerfile`
If it copies `dist/OpenLibertyApp.war`, change the source path to `target/OpenLibertyApp.war`. Leave the destination unchanged.

- [ ] **Step 6: Remove the Ant build files**

```bash
git rm jakarta-ee/student-web-app/build.xml jakarta-ee/student-web-app/build.properties
```

- [ ] **Step 7: Commit**

```bash
git add jakarta-ee/student-web-app
git commit -m "feat: migrate student-web-app from Ant to Maven"
```

---

### Task 003: Externalize localhost resources for Azure

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`
- Modify: `asset-manager/worker/src/main/resources/application.properties`
- Modify: `todo-web-api-use-oracle-db/src/main/resources/application.yaml`
- Modify: `jakarta-ee/student-web-app/docker-compose.yml`
- Modify: `jakarta-ee/student-web-app/liberty_config/server-docker.xml`

> **Depends on:** Task 002. Goal: no hard-coded `localhost`; all endpoints/credentials come from environment with sane local defaults. (Credential *values* are fully removed in Phase 3 Tasks 010/012; here we externalize the **endpoints** and parameterize.)

- [ ] **Step 1: Externalize asset-manager **web** datasource + RabbitMQ**

In `asset-manager/web/src/main/resources/application.properties`, replace the RabbitMQ and Database blocks:
```properties
# RabbitMQ Configuration
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:guest}

# Database Configuration
spring.datasource.url=${DATASOURCE_URL:jdbc:postgresql://localhost:5432/assets_manager}
spring.datasource.username=${DATASOURCE_USERNAME:postgres}
spring.datasource.password=${DATASOURCE_PASSWORD:postgres}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.show-sql=true
```

- [ ] **Step 2: Externalize asset-manager **worker** datasource + RabbitMQ**

Apply the same RabbitMQ and Database replacement in `asset-manager/worker/src/main/resources/application.properties` (the worker file has no `spring.jpa.show-sql` line — leave its existing JPA lines intact, only swap host/url/credentials to the `${VAR:default}` form shown above).

- [ ] **Step 3: Externalize todo Oracle datasource**

In `todo-web-api-use-oracle-db/src/main/resources/application.yaml`, replace the `datasource` block:
```yaml
spring:
  datasource:
    url: ${DATASOURCE_URL:jdbc:oracle:thin:@localhost:1521/XEPDB1}
    username: ${DATASOURCE_USERNAME:system}
    password: ${DATASOURCE_PASSWORD:oracle}
    driver-class-name: oracle.jdbc.OracleDriver
```
(Leave the `jpa`/`sql`/`server` sections unchanged.)

- [ ] **Step 4: Externalize student-web-app docker-compose DB host**

In `jakarta-ee/student-web-app/docker-compose.yml`, parameterize the `app` service env so the JDBC host/credentials come from the environment (defaults preserve local behavior):
```yaml
    environment:
      - JDBC_URL=${JDBC_URL:-jdbc:mysql://mysql:3306/studentdb?useSSL=true&serverTimezone=UTC}
      - DB_USER=${DB_USER:-student}
      - DB_PASSWORD=${DB_PASSWORD:-studentpass}
```
Note: `useSSL=false&allowPublicKeyRetrieval=true` is changed to `useSSL=true` for Azure Database for MySQL readiness.

- [ ] **Step 5: Externalize Liberty datasource config**

Run: `grep -n "localhost\|jdbc:\|user=\|password=" jakarta-ee/student-web-app/liberty_config/server-docker.xml`
Replace any literal host/user/password in the `<dataSource>`/`<properties.*>` element with Liberty variables that read the same env vars, e.g.:
```xml
<dataSource jndiName="jdbc/studentDS">
    <jdbcDriver libraryRef="mysqlLib"/>
    <properties.mysql url="${JDBC_URL}" user="${DB_USER}" password="${DB_PASSWORD}"/>
</dataSource>
```
Keep `jndiName` and `libraryRef` exactly as they already appear in the file.

- [ ] **Step 6: Verify nothing else hard-codes localhost in shipped config**

Run:
```bash
grep -rn "localhost" \
  asset-manager/web/src/main/resources \
  asset-manager/worker/src/main/resources \
  todo-web-api-use-oracle-db/src/main/resources \
  jakarta-ee/student-web-app/liberty_config
```
Expected: only the `${VAR:localhost}` **defaults** remain (no bare `localhost`).

- [ ] **Step 7: Rebuild affected modules**

Run:
```bash
( cd asset-manager && mvn -q clean verify ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q clean verify ) ; echo "todo exit=$?"
( cd jakarta-ee/student-web-app && mvn -q clean package ) ; echo "student exit=$?"
```
Expected: all `exit=0`.

- [ ] **Step 8: Commit**

```bash
git add asset-manager todo-web-api-use-oracle-db jakarta-ee/student-web-app
git commit -m "feat: externalize localhost resources via environment for Azure readiness"
```

---

# Phase 3 — Security Remediation (CWE)

> Run Tasks 004–018 **in order**. Several touch `AbstractFileProcessingService.java`, `LocalFileStorageService.java`, and `S3Controller.java`; sequencing avoids conflicts. After each task, rebuild the affected module(s) and commit.

### Task 004: CWE-477 — Use of Obsolete Function

**Files:**
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/util/MyBatisUtil.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/AddStudentServlet.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/service/StudentService.java`
- Modify: `jakarta-ee/student-web-app/pom.xml`

Obsolete APIs in use: iBATIS 2.x (`com.ibatis.*`), Log4j 1.x (`org.apache.log4j.Logger`), Codehaus Jackson (`org.codehaus.jackson.*`).

- [ ] **Step 1: Replace obsolete dependencies in `pom.xml`**

In `jakarta-ee/student-web-app/pom.xml`, swap the three obsolete deps for maintained equivalents:
```xml
        <!-- MyBatis 3 (replaces iBATIS 2.x) -->
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
            <version>3.5.16</version>
        </dependency>
        <!-- SLF4J + Logback (replaces Log4j 1.x) -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.13</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.6</version>
        </dependency>
        <!-- Jackson 2 (replaces Codehaus Jackson) -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.2</version>
        </dependency>
```
Remove the old `ibatis-sqlmap`, `log4j:log4j`, and `org.codehaus.jackson:jackson-mapper-asl` entries.

- [ ] **Step 2: Migrate `MyBatisUtil` to MyBatis 3 `SqlSessionFactory`**

Replace the body of `MyBatisUtil.java`:
```java
package org.sample.azure.student.coreft.util;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.Reader;

public class MyBatisUtil {
    private static SqlSessionFactory sqlSessionFactory;
    private static Exception initializationException;

    static {
        try {
            Reader reader = Resources.getResourceAsReader("mybatis-config.xml");
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
        } catch (Exception e) {
            initializationException = e;
        }
    }

    public static SqlSessionFactory getSqlSessionFactory() {
        if (sqlSessionFactory == null) {
            throw new IllegalStateException(
                "SqlSessionFactory not initialized", initializationException);
        }
        return sqlSessionFactory;
    }
}
```
Rename `src/main/resources/sql-map-config.xml` to `mybatis-config.xml` and convert it to MyBatis 3 config DTD (`<configuration>/<environments>/<mappers>`). Convert `Student_SqlMap.xml` to a MyBatis 3 `<mapper namespace="com.azure.sample.StudentMapper">` with `listStudent`/`addStudent` statements (same SQL).

- [ ] **Step 3: Update the servlets/service to the MyBatis 3 `SqlSession` API + SLF4J**

In `StudentProfileListServlet.java`, `AddStudentServlet.java`, `StudentService.java`:
```java
// logging imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ...
private static final Logger logger = LoggerFactory.getLogger(StudentProfileListServlet.class);
```
```java
// data access (replaces com.ibatis SqlMapSession)
import org.apache.ibatis.session.SqlSession;
// list:
try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
    List<StudentProfile> students =
        session.selectList("com.azure.sample.StudentMapper.listStudent");
    // ...render...
}
// insert (AddStudentServlet):
try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
    session.insert("com.azure.sample.StudentMapper.addStudent", params);
    session.commit();
}
```
Replace the Codehaus `org.codehaus.jackson.map.ObjectMapper` import with `com.fasterxml.jackson.databind.ObjectMapper` (the `writeValueAsString` call is unchanged).

- [ ] **Step 4: Build student-web-app**

Run: `cd jakarta-ee/student-web-app && mvn -q clean package ; echo "exit=$?" ; cd -`
Expected: `exit=0`, `target/OpenLibertyApp.war` produced.

- [ ] **Step 5: Verify no obsolete APIs remain**

Run:
```bash
grep -rn "com.ibatis\|org.apache.log4j\|org.codehaus.jackson" jakarta-ee/student-web-app/src
```
Expected: no matches.

- [ ] **Step 6: Commit**

```bash
git add jakarta-ee/student-web-app
git commit -m "fix(security): CWE-477 replace obsolete iBATIS/Log4j1/Codehaus-Jackson APIs"
```

---

### Task 005: CWE-665 — Improper Initialization

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`

- [ ] **Step 1: Guard `rootLocation` use in `LocalFileStorageService`**

`rootLocation` is only assigned in `@PostConstruct init()`. Add a private guard and call it at the top of every public method that dereferences `rootLocation` (`listObjects`, `uploadObject`, `getObject`, `deleteObject`):
```java
    private void ensureInitialized() {
        if (rootLocation == null) {
            throw new IllegalStateException("Storage not initialized: rootLocation is null");
        }
    }
```
Add `ensureInitialized();` as the first statement inside `getObject`, `deleteObject`, `uploadObject`, and at the start of the `try` in `listObjects`.

- [ ] **Step 2: Initialize the PNG write-param branch safely in `AbstractFileProcessingService`**

In `generateThumbnail`, the PNG branch declares `ImageWriteParam pngWriteParam = null;` then conditionally uses it. Restructure so the writer and param are obtained together and only used when non-null (no reliance on a possibly-unset param). Replace the PNG `else`-branch body with a guarded local-scope block:
```java
        } else if (extension.equalsIgnoreCase("png")) {
            javax.imageio.ImageWriter pngWriter =
                ImageIO.getImageWritersByFormatName("png").next();
            javax.imageio.ImageWriteParam pngWriteParam = pngWriter.getDefaultWriteParam();
            if (pngWriteParam.canWriteCompressed()) {
                pngWriteParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                pngWriteParam.setCompressionType("Deflate");
                pngWriteParam.setCompressionQuality(0.0f);
                writeWithWriter(pngWriter, pngWriteParam, resultImage, output);
            } else {
                pngWriter.dispose();
                ImageIO.write(resultImage, extension, output.toFile());
            }
        } else {
            ImageIO.write(resultImage, extension, output.toFile());
        }
```
(`writeWithWriter` is introduced in Task 007 — for this task, inline the existing write/dispose/close logic; Task 007 extracts it into the helper. If executing out of order, keep the inline version here.)

- [ ] **Step 3: Build the worker and web modules**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-665 guard lazy/conditional initialization"
```

---

### Task 006: CWE-681 — Incorrect Numeric Conversion

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`

- [ ] **Step 1: Round instead of truncate thumbnail dimensions**

In `generateThumbnail`, the casts `(int) (maxDimension / aspectRatio)` and `(int) (maxDimension * aspectRatio)` truncate. Replace with rounding and a minimum of 1 px:
```java
        if (originalWidth > originalHeight) {
            thumbnailWidth = maxDimension;
            thumbnailHeight = Math.max(1, (int) Math.round(maxDimension / aspectRatio));
        } else {
            thumbnailHeight = maxDimension;
            thumbnailWidth = Math.max(1, (int) Math.round(maxDimension * aspectRatio));
        }
```

- [ ] **Step 2: Keep progressive-scaling halving safe**

In `progressiveScaling`, integer division `currentWidth / 2` is acceptable (intended halving), but guard the lower bound so it never drops below the target:
```java
            int newWidth = Math.max(currentWidth / 2, targetWidth);
            int newHeight = Math.max(currentHeight / 2, targetHeight);
```
(These already use `Math.max`; confirm no other `(int)` cast of a double remains uncorrected.)
Run: `grep -n "(int)" asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`
Expected: only the two rounded conversions from Step 1 remain as double→int.

- [ ] **Step 3: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-681 round double-to-int thumbnail dimension conversions"
```

---

### Task 007: CWE-772 — Missing Release of Resource

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`

- [ ] **Step 1: Add a try-with-resources writer helper**

Add a private helper that guarantees `ImageOutputStream` close and `ImageWriter` dispose on all paths:
```java
    private void writeWithWriter(javax.imageio.ImageWriter writer,
                                 javax.imageio.ImageWriteParam param,
                                 BufferedImage image,
                                 Path output) throws IOException {
        try (javax.imageio.stream.ImageOutputStream out =
                 javax.imageio.ImageIO.createImageOutputStream(output.toFile())) {
            writer.setOutput(out);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
```

- [ ] **Step 2: Route the JPEG and PNG branches through the helper**

Replace the JPEG branch's manual `setOutput/write/dispose/close` with:
```java
        if (extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg")) {
            javax.imageio.ImageWriter jpgWriter =
                javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
            jpgWriteParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            jpgWriteParam.setCompressionQuality(0.95f);
            writeWithWriter(jpgWriter, jpgWriteParam, resultImage, output);
        }
```
The PNG branch already calls `writeWithWriter` after Task 005's restructure.

- [ ] **Step 3: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-772 release ImageWriter/stream via try-with-resources"
```

---

### Task 008: CWE-775 — Missing Release of File Descriptor

**Files:**
- Modify: `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`

- [ ] **Step 1: Confirm all `ImageOutputStream` creation is inside try-with-resources**

After Task 007, every `createImageOutputStream(...)` is inside `writeWithWriter`'s try-with-resources. Verify no stray stream remains:
Run: `grep -n "createImageOutputStream\|\.close()" asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`
Expected: a single `createImageOutputStream` call (inside the helper) and **no** manual `.close()` on image streams.

- [ ] **Step 2: Ensure temp-dir cleanup releases handles even on early failure**

In `processImage`'s `finally`, `Files.deleteIfExists(tempDir)` fails silently if the dir is non-empty. Make cleanup order-safe (files before dir) — confirm originalFile/thumbnailFile are deleted before tempDir (they are) and leave as-is. No code change unless the grep in Step 1 finds a leak.

- [ ] **Step 3: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-775 guarantee image-output file descriptor release"
```

---

### Task 009: CWE-1057 — Data Access Outside Data Manager

**Files:**
- Modify: `todo-web-api-use-oracle-db/src/main/java/com/microsoft/migration/todo/service/TodoService.java`
- Modify: `todo-web-api-use-oracle-db/src/main/java/com/microsoft/migration/todo/repository/TodoRepository.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/service/StudentService.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/AddStudentServlet.java`

> Move raw data access into the DAO/repository layer. `OracleSqlDemonstrator` is an intentional Oracle demo — leave its raw JDBC but confirm it is only invoked through the controller's demo endpoints (no change required there for this CWE).

- [ ] **Step 1: Move todo native queries into the repository**

In `TodoRepository` (a `JpaRepository`), add `@Query(nativeQuery=true)` methods replacing the `EntityManager.createNativeQuery` calls in `TodoService`:
```java
    @Query(value = "SELECT * FROM TODO_ITEMS WHERE DUE_DATE < SYSDATE AND COMPLETED = 0 " +
                   "ORDER BY PRIORITY DESC, DUE_DATE ASC", nativeQuery = true)
    List<TodoItem> findOverdue();

    @Modifying
    @Query(value = "UPDATE TODO_ITEMS SET PRIORITY = :newPriority, UPDATED_AT = SYSTIMESTAMP " +
                   "WHERE DUE_DATE < :cutoffDate AND COMPLETED = 0", nativeQuery = true)
    int bumpPriorityBefore(@Param("cutoffDate") LocalDateTime cutoffDate,
                           @Param("newPriority") int newPriority);

    @Query(value = "SELECT * FROM TODO_ITEMS WHERE DBMS_LOB.INSTR(TITLE, :term) > 0 " +
                   "OR DBMS_LOB.INSTR(DESCRIPTION, :term) > 0", nativeQuery = true)
    List<TodoItem> searchVarchar2(@Param("term") String term);
```
Add imports `org.springframework.data.jpa.repository.Query`, `Modifying`, `org.springframework.data.repository.query.Param`, `java.time.LocalDateTime`, `java.util.List`, the `TodoItem` type.

- [ ] **Step 2: Delegate from `TodoService`; drop the `EntityManager`**

Replace `getOverdueTasks`, `updateTasksWithOracle`, `searchWithOracleVarchar2` bodies with repository calls and remove the `@PersistenceContext EntityManager entityManager` field and its imports:
```java
    @Transactional
    public List<TodoItem> getOverdueTasks() {
        return todoRepository.findOverdue();
    }

    @Transactional
    public void updateTasksWithOracle(LocalDateTime cutoffDate, int newPriority) {
        todoRepository.bumpPriorityBefore(cutoffDate, newPriority);
    }

    @Transactional
    public List<TodoItem> searchWithOracleVarchar2(String searchTerm) {
        return todoRepository.searchVarchar2(searchTerm);
    }
```

- [ ] **Step 3: Route student servlets through a DAO**

In `StudentService` add `listStudents()` and `addStudent(Map<String,Object>)` that own the `SqlSession` open/select/insert/commit (the MyBatis 3 calls from Task 004). Replace the inline `session.selectList(...)`/`session.insert(...)` blocks in `StudentProfileListServlet` and `AddStudentServlet` with `studentService.listStudents()` / `studentService.addStudent(params)`. The servlets keep only request parsing and HTML rendering.

- [ ] **Step 4: Build both modules**

Run:
```bash
( cd todo-web-api-use-oracle-db && mvn -q clean verify ) ; echo "todo exit=$?"
( cd jakarta-ee/student-web-app && mvn -q clean package ) ; echo "student exit=$?"
```
Expected: both `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add todo-web-api-use-oracle-db jakarta-ee/student-web-app
git commit -m "fix(security): CWE-1057 move data access into repository/DAO layer"
```

---

### Task 010: CWE-259 — Hard-coded Password

**Files:**
- Modify: `todo-web-api-use-oracle-db/src/main/resources/application.yaml`
- Modify: `asset-manager/web/src/main/resources/application.properties`
- Modify: `asset-manager/worker/src/main/resources/application.properties`
- Modify: `jakarta-ee/student-web-app/docker-compose.yml`
- Modify: `Malshinon/DBConnection.cs`

> Phase 2 externalized endpoints with defaults; this task **removes hard-coded password literals** (no plaintext defaults for secrets).

- [ ] **Step 1: Remove password defaults in Spring config**

Change the externalized `password` placeholders so they have **no** default value (fail fast if unset). In `application.properties` (web + worker) and `application.yaml` (todo):
```properties
spring.datasource.password=${DATASOURCE_PASSWORD}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
```
```yaml
    password: ${DATASOURCE_PASSWORD}
```
Leave username/host defaults — only secrets lose their defaults.

- [ ] **Step 2: Remove the compose password literals**

In `jakarta-ee/student-web-app/docker-compose.yml`, require the MySQL/app secrets from the environment with no inline literals:
```yaml
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: studentdb
      MYSQL_USER: ${DB_USER:-student}
      MYSQL_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD}
```
And the `app` service `DB_PASSWORD=${DB_PASSWORD:?set DB_PASSWORD}` (no default).

- [ ] **Step 3: Externalize the C# connection string**

In `Malshinon/DBConnection.cs`, read the connection string from configuration/environment instead of the hard-coded literal:
```csharp
        public static MySqlConnection Connect(string cs = null)
        {
            var connStr = string.IsNullOrWhiteSpace(cs)
                ? Environment.GetEnvironmentVariable("MALSHINON_CONNSTR")
                  ?? throw new InvalidOperationException("MALSHINON_CONNSTR is not set")
                : cs;

            MySqlConnection conn = new MySqlConnection(connStr);
            conn.Open();
            return conn;
        }
```

- [ ] **Step 4: Verify no password literals remain**

Run:
```bash
grep -rn "password=postgres\|password: oracle\|MYSQL_PASSWORD: \|password=;database" \
  asset-manager todo-web-api-use-oracle-db jakarta-ee/student-web-app Malshinon
```
Expected: no matches.

- [ ] **Step 5: Build the Java modules (and C# if toolchain available)**

Run:
```bash
( cd asset-manager && mvn -q clean verify ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q clean verify ) ; echo "todo exit=$?"
```
Expected: both `exit=0`. (Spring will only fail at runtime if the env var is missing, which is the intended fail-fast behavior.)

- [ ] **Step 6: Commit**

```bash
git add asset-manager todo-web-api-use-oracle-db jakarta-ee/student-web-app Malshinon
git commit -m "fix(security): CWE-259 remove hard-coded passwords; require from environment"
```

---

### Task 011: CWE-778 — Insufficient Logging

**Files:**
- Modify: `todo-web-api-use-oracle-db/src/main/java/com/microsoft/migration/todo/controller/TodoController.java`

- [ ] **Step 1: Add a logger and log the swallowed failures**

`updateTodo` and `deleteTodo` catch exceptions and return 404 with no log. Add an SLF4J logger and log at WARN with context (no sensitive data):
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ...
public class TodoController {

    private static final Logger logger = LoggerFactory.getLogger(TodoController.class);
```
```java
    @PutMapping("/{id}")
    public ResponseEntity<TodoItem> updateTodo(@PathVariable Long id, @RequestBody TodoItem todoDetails) {
        try {
            return ResponseEntity.ok(todoService.updateTodo(id, todoDetails));
        } catch (RuntimeException ex) {
            logger.warn("Update failed for todo id={}: {}", id, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        try {
            todoService.deleteTodo(id);
            return ResponseEntity.noContent().build();
        } catch (Exception ex) {
            logger.warn("Delete failed for todo id={}: {}", id, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
```

- [ ] **Step 2: Build**

Run: `cd todo-web-api-use-oracle-db && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add todo-web-api-use-oracle-db
git commit -m "fix(security): CWE-778 log security-relevant update/delete failures"
```

---

### Task 012: CWE-798 — Hard-coded Credentials

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`
- Modify: `asset-manager/worker/src/main/resources/application.properties`

> Task 010 removed password literals. This task removes the remaining **username/credential pairs** and the AWS access/secret keys, moving to env/managed identity.

- [ ] **Step 1: Externalize datasource usernames and RabbitMQ user**

In both `application.properties` files, replace remaining literal usernames:
```properties
spring.datasource.username=${DATASOURCE_USERNAME}
spring.rabbitmq.username=${RABBITMQ_USERNAME}
```

- [ ] **Step 2: Externalize AWS keys (or drop for managed identity)**

Replace the placeholder AWS keys with env references (or remove if S3 uses the default credential provider chain / managed identity):
```properties
aws.accessKey=${AWS_ACCESS_KEY:}
aws.secretKey=${AWS_SECRET_KEY:}
```
(worker uses `aws.accessKeyId` — apply `${AWS_ACCESS_KEY_ID:}` there.)

- [ ] **Step 3: Verify no credential literals remain**

Run:
```bash
grep -rn "username=postgres\|username=guest\|your-access-key\|your-secret-key" \
  asset-manager/web/src/main/resources asset-manager/worker/src/main/resources
```
Expected: no matches.

- [ ] **Step 4: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-798 externalize remaining credentials to environment"
```

---

### Task 013: CWE-22 — Path Traversal

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`

> Tasks 013–015 and 018 all harden the `key`→path resolution in `LocalFileStorageService`. Build a single reusable resolver in Task 013; later tasks tighten it.

- [ ] **Step 1: Add a safe-resolve helper**

Add a method that canonicalizes the resolved path and asserts containment within `rootLocation`:
```java
    private Path resolveSafe(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("Invalid key");
        }
        Path resolved = rootLocation.resolve(key).normalize();
        if (!resolved.startsWith(rootLocation)) {
            throw new IOException("Access outside storage root is not allowed: " + key);
        }
        return resolved;
    }
```

- [ ] **Step 2: Route `getObject` and `deleteObject` through it**

Replace `rootLocation.resolve(key)` in `getObject` and `deleteObject` with `resolveSafe(key)` (keep the existing `getThumbnailKey(key)` call routed through `resolveSafe` too):
```java
    @Override
    public InputStream getObject(String key) throws IOException {
        Path file = resolveSafe(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        return new BufferedInputStream(Files.newInputStream(file));
    }
```
```java
    @Override
    public void deleteObject(String key) throws IOException {
        Path file = resolveSafe(key);
        // ...existing existence check + delete...
        Path thumbnailFile = resolveSafe(getThumbnailKey(key));
        // ...existing thumbnail delete...
    }
```

- [ ] **Step 3: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-22 enforce storage-root containment on key resolution"
```

---

### Task 014: CWE-23 — Relative Path Traversal

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`

- [ ] **Step 1: Reject relative-traversal sequences explicitly**

Strengthen `resolveSafe` to reject `..` segments before normalization (defense in depth; the `startsWith` check already blocks escape, this gives a clear early failure):
```java
    private Path resolveSafe(String key) throws IOException {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new IOException("Invalid or unsafe key: " + key);
        }
        Path resolved = rootLocation.resolve(key).normalize();
        if (!resolved.startsWith(rootLocation)) {
            throw new IOException("Access outside storage root is not allowed: " + key);
        }
        return resolved;
    }
```

- [ ] **Step 2: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-23 reject relative-path traversal sequences in keys"
```

---

### Task 015: CWE-36 — Absolute Path Traversal

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`

- [ ] **Step 1: Reject absolute keys**

Add an absolute-path guard to `resolveSafe` so an absolute `key` cannot discard the base directory:
```java
        if (key == null || key.isBlank() || key.contains("..") || Paths.get(key).isAbsolute()) {
            throw new IOException("Invalid or unsafe key: " + key);
        }
```

- [ ] **Step 2: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-36 reject absolute-path keys in storage resolution"
```

---

### Task 016: CWE-434 — Unrestricted File Upload

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java`

- [ ] **Step 1: Add an allow-list constant**

In `LocalFileStorageService`, add allowed extensions/MIME types (images, matching the thumbnail use case):
```java
    private static final java.util.Set<String> ALLOWED_EXT =
        java.util.Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final java.util.Set<String> ALLOWED_MIME =
        java.util.Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
```

- [ ] **Step 2: Validate in `uploadObject`**

After cleaning the filename and before `Files.copy`, enforce the allow-list:
```java
        String ext = StorageUtil.getExtension(filename).replaceFirst("^\\.", "").toLowerCase();
        String contentType = file.getContentType();
        if (!ALLOWED_EXT.contains(ext) ||
            contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase())) {
            throw new IOException("Unsupported file type: " + filename + " (" + contentType + ")");
        }
```
(If `StorageUtil` is in the web module, replace with the existing extension helper used there; the worker's `StorageUtil.getExtension` is in the worker module. Use a local extension parse if no web helper exists.)

- [ ] **Step 3: Surface validation errors in the controller**

`S3Controller.uploadObject` already catches `IOException` and flashes the message — confirm the rejected-type message reaches the user. No change needed beyond Step 2 unless a generic catch hides it.

- [ ] **Step 4: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-434 enforce upload allow-list (extension + MIME)"
```

---

### Task 017: CWE-79 — Cross-site Scripting

**Files:**
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java`

- [ ] **Step 1: HTML-escape all user-controlled output**

`doGet` concatenates `student.getName()/getEmail()/getMajor()` and reflects `ex.getMessage()` raw into HTML. Add an escape helper and apply it to every dynamic value:
```java
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
```
```java
                for (StudentProfile student : students) {
                    out.println("<tr><td>" + esc(String.valueOf(student.getId())) + "</td>" +
                               "<td>" + esc(student.getName()) + "</td>" +
                               "<td>" + esc(student.getEmail()) + "</td>" +
                               "<td>" + esc(student.getMajor()) + "</td></tr>");
                }
```
And the error line:
```java
                out.println("<p>Error: " + esc(ex.getMessage()) + "</p>");
```
(After Task 009, list rendering may delegate to a DAO but the servlet still owns HTML output — apply `esc` wherever the servlet writes dynamic values.)

- [ ] **Step 2: Build**

Run: `cd jakarta-ee/student-web-app && mvn -q clean package ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add jakarta-ee/student-web-app
git commit -m "fix(security): CWE-79 HTML-escape user-controlled servlet output"
```

---

### Task 018: CWE-99 — Resource Injection

**Files:**
- Modify: `asset-manager/web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java`

- [ ] **Step 1: Validate the `key` path variable at the controller boundary**

`viewObject` and `deleteObject` pass `@PathVariable String key` straight to the service. Add a controller-level validation that rejects path separators/traversal before calling the service (the service's `resolveSafe` is the second layer):
```java
    private static boolean isValidKey(String key) {
        return key != null && !key.isBlank()
            && !key.contains("..")
            && !key.contains("/") && !key.contains("\\");
    }
```
```java
    @GetMapping("/view/{key}")
    public ResponseEntity<InputStreamResource> viewObject(@PathVariable String key) {
        if (!isValidKey(key)) {
            return ResponseEntity.badRequest().build();
        }
        // ...existing body...
    }
```
```java
    @PostMapping("/delete/{key}")
    public String deleteObject(@PathVariable String key, RedirectAttributes redirectAttributes) {
        if (!isValidKey(key)) {
            redirectAttributes.addFlashAttribute("error", "Invalid file identifier");
            return "redirect:/" + StorageConstants.STORAGE_PATH;
        }
        // ...existing body...
    }
```

- [ ] **Step 2: Build**

Run: `cd asset-manager && mvn -q clean verify ; echo "exit=$?" ; cd -`
Expected: `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add asset-manager
git commit -m "fix(security): CWE-99 validate resource identifiers at controller boundary"
```

---

# Phase 4 — Dependency CVE Remediation

### Task 019: Scan and fix CVEs in declared dependencies

**Files:**
- Modify: `asset-manager/web/pom.xml`
- Modify: `asset-manager/worker/pom.xml`
- Modify: `todo-web-api-use-oracle-db/pom.xml`
- Modify: `rabbitmq-sender/pom.xml`

> After Phase 1, all modules inherit Spring Boot 3.5.x, whose managed BOM already pins patched `postgresql`, `jackson-databind`, and `spring-boot-devtools`. This task **verifies** the managed versions are patched and pins explicit overrides only where a CVE remains.

- [ ] **Step 1: Generate a dependency CVE report**

Run from each module root (uses the OWASP plugin without editing poms):
```bash
( cd asset-manager && mvn -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 ) ; echo "todo exit=$?"
( cd rabbitmq-sender && mvn -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 ) ; echo "rabbitmq exit=$?"
```
Read the generated `target/dependency-check-report.html` per module. Record each finding: dependency, current (resolved) version, CVE id, fixed version.

- [ ] **Step 2: Confirm Spring Boot 3.5.x manages the previously-flagged deps**

Run:
```bash
( cd asset-manager/web && mvn -q dependency:list | grep -i "postgresql\|jackson-databind\|devtools" )
( cd asset-manager/worker && mvn -q dependency:list | grep -i "postgresql\|jackson-databind" )
( cd todo-web-api-use-oracle-db && mvn -q dependency:list | grep -i "devtools" )
( cd rabbitmq-sender && mvn -q dependency:list | grep -i "jackson-databind" )
```
Expected: postgresql ≥ 42.7.x, jackson-databind ≥ 2.17.x, devtools ≥ 3.5.x (all patched). The old `org.postgresql:postgresql 42.6.1` from the pre-upgrade BOM should no longer appear.

- [ ] **Step 3: Pin explicit overrides only for residual findings**

If Step 1 still reports a CVSS ≥ 7 CVE for a transitive dependency the BOM does not patch, add a `<properties>` override (Spring Boot honors version properties) or an explicit managed dependency in the affected `pom.xml`. Example (only if jackson-databind is still flagged):
```xml
    <properties>
        <jackson-bom.version>2.17.2</jackson-bom.version>
    </properties>
```
Document each override: dependency, old version, new version, and breaking-change risk (jackson/postgres patch bumps are non-breaking; a major bump must be noted).

- [ ] **Step 4: Re-run the scan to confirm zero high findings**

Run:
```bash
( cd asset-manager && mvn -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 ) ; echo "todo exit=$?"
( cd rabbitmq-sender && mvn -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 ) ; echo "rabbitmq exit=$?"
```
Expected: all `exit=0` (no CVSS ≥ 7 findings).

- [ ] **Step 5: Full build of every module**

Run:
```bash
( cd asset-manager && mvn -q clean verify ) ; echo "asset-manager exit=$?"
( cd todo-web-api-use-oracle-db && mvn -q clean verify ) ; echo "todo exit=$?"
( cd rabbitmq-sender && mvn -q clean verify ) ; echo "rabbitmq exit=$?"
( cd jakarta-ee/student-web-app && mvn -q clean package ) ; echo "student exit=$?"
( cd mi-sql-public-demo && mvn -q clean verify ) ; echo "mi-sql exit=$?"
```
Expected: all `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add asset-manager todo-web-api-use-oracle-db rabbitmq-sender
git commit -m "fix(security): remediate dependency CVEs to patched versions"
```

---

## Self-Review (completed by plan author)

**1. Spec coverage** — every `tasks.json` id is mapped:
001→Task 001, 002→Task 002, 003→Task 003, 004–018→Tasks 004–018 (one CWE each), 019→Task 019. ✅

**2. Placeholder scan** — no "TBD/implement later/add appropriate X". Where the existing modules lack a helper (web-module `StorageUtil`, iBATIS jar availability), the step names the concrete fallback rather than hand-waving. ✅

**3. Type consistency** — shared symbols are consistent across tasks: `resolveSafe(String)` is introduced in Task 013 and only tightened (not renamed) in 014/015; `writeWithWriter(...)` is introduced in Task 007 and referenced by the PNG branch restructured in Task 005 (cross-reference noted in both tasks); `ensureInitialized()` (Task 005), `esc(String)` (Task 017), `isValidKey(String)`/`resolveSafe` two-layer validation (Task 018) are each defined where first used. ✅

**4. Cross-cutting compatibility risk called out** — Java 25 forces Spring Boot bumps (and `javax`→`jakarta`), documented in the Scope Check and folded into Task 001. ✅

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-30-lab2-modernization.md`. Two execution options:

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

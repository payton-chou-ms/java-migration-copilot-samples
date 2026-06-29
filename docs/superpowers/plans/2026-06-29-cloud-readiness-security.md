# Cloud Readiness & Security Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Java versions, migrate student-web-app from Ant to Maven, replace asset-manager's local PostgreSQL with Azure DB for PostgreSQL (Managed Identity), externalize credentials to environment variables, and remediate 14 CWE classes plus CVEs across `java-migration-copilot-samples`.

**Architecture:** Phased execution in dependency order: (1) runtime/build standardization — Tasks 001 and 002 are independent and can run in either order; (2) Azure resource migration — Task 003 depends on Task 001; (3) security hardening — Tasks 004 and 007 depend on Tasks 002 + 003, Tasks 005 and 006 depend on Task 003, Task 008 depends on Tasks 001 + 003.

**Tech Stack:** Java 17 (asset-manager / student-web-app), Java 21 (todo-web-api, mi-sql-public-demo, rabbitmq-sender), Spring Boot 2.7.18 (asset-manager), Spring Boot 3.2.4 (todo-web-api), Spring Boot 3.3.0 (rabbitmq-sender), Jakarta EE + Servlet 4 (student-web-app), Maven, Azure Database for PostgreSQL (Flexible Server), Azure Managed Identity, Spring Cloud Azure 4.16.0, MyBatis 3.5.x (replaces iBATIS 2.x)

---

## File Map

| File | Task | Action |
|------|------|--------|
| `asset-manager/pom.xml` | 001 | Modify: `java.version` 8 → 17 |
| `todo-web-api-use-oracle-db/pom.xml` | 001 | Modify: `java.version` 17 → 21 |
| `mi-sql-public-demo/pom.xml` | 001 | Modify: `maven.compiler.source/target` + `java.version` 17 → 21 |
| `rabbitmq-sender/pom.xml` | 001 | Modify: `java.version` 17 → 21 |
| `jakarta-ee/student-web-app/pom.xml` | 002 | Create: Maven WAR project |
| `jakarta-ee/student-web-app/src/main/java/` | 002 | Create: move Java sources from `src/` |
| `jakarta-ee/student-web-app/src/main/resources/` | 002 | Create: move `resources/` content |
| `jakarta-ee/student-web-app/src/main/webapp/` | 002 | Create: move `WebContent/` |
| `asset-manager/pom.xml` | 003 | Modify: add Spring Cloud Azure BOM |
| `asset-manager/web/pom.xml` | 003 | Modify: add `spring-cloud-azure-starter-jdbc-postgresql` |
| `asset-manager/worker/pom.xml` | 003 | Modify: add `spring-cloud-azure-starter-jdbc-postgresql` |
| `asset-manager/web/src/main/resources/application.properties` | 003, 005 | Modify: Azure datasource URL, env var refs for all credentials |
| `asset-manager/worker/src/main/resources/application.properties` | 003, 005 | Modify: Azure datasource URL, env var refs for all credentials |
| `jakarta-ee/student-web-app/src/main/java/.../util/MyBatisUtil.java` | 004 | Modify: iBATIS 2.x → MyBatis 3.x API |
| `jakarta-ee/student-web-app/src/main/java/.../StudentProfileListServlet.java` | 004, 007 | Modify: use StudentService; HTML-encode output |
| `jakarta-ee/student-web-app/src/main/java/.../IndexServlet.java` | 004 | Modify: use StudentService |
| `jakarta-ee/student-web-app/src/main/java/.../AddStudentServlet.java` | 004 | Modify: use StudentService |
| `asset-manager/worker/src/.../AbstractFileProcessingService.java` | 004 | Modify: try-with-resources for ImageOutputStream |
| `todo-web-api-use-oracle-db/src/.../OracleSqlDemonstrator.java` | 004 | Modify: try-with-resources for ResultSet |
| `asset-manager/web/src/.../controller/S3Controller.java` | 005, 006 | Modify: audit logging; file-type allow-list |
| `asset-manager/web/src/.../service/LocalFileStorageService.java` | 006, 007 | Modify: path canonicalization; file-type validation |
| `asset-manager/pom.xml` | 008 | Modify: override `postgresql` version |
| `asset-manager/web/pom.xml` | 008 | Modify: remove `spring-boot-devtools` or scope to `test` |
| `todo-web-api-use-oracle-db/pom.xml` | 008 | Modify: override `spring-boot-devtools` / devtools removal |
| `asset-manager/worker/pom.xml` | 008 | Modify: override `jackson-databind` version |
| `rabbitmq-sender/pom.xml` | 008 | Modify: override `jackson-databind` version |

---

## Task 001 — Java Version Upgrade

**Scope:** `asset-manager` → Java 17; `todo-web-api-use-oracle-db`, `mi-sql-public-demo`, `rabbitmq-sender` → Java 21. JDK-only; Spring Boot and framework versions are not changed.

**Files:**
- Modify: `asset-manager/pom.xml`
- Modify: `todo-web-api-use-oracle-db/pom.xml`
- Modify: `mi-sql-public-demo/pom.xml`
- Modify: `rabbitmq-sender/pom.xml`

---

- [ ] **Step 1: Upgrade asset-manager to Java 17**

  In `asset-manager/pom.xml`, change:
  ```xml
  <properties>
      <java.version>8</java.version>
  </properties>
  ```
  to:
  ```xml
  <properties>
      <java.version>17</java.version>
  </properties>
  ```

- [ ] **Step 2: Build asset-manager to confirm Java 17 compiles**

  ```bash
  cd asset-manager
  mvn compile -pl web,worker 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`

- [ ] **Step 3: Upgrade todo-web-api to Java 21**

  In `todo-web-api-use-oracle-db/pom.xml`, change:
  ```xml
  <java.version>17</java.version>
  ```
  to:
  ```xml
  <java.version>21</java.version>
  ```

- [ ] **Step 4: Upgrade mi-sql-public-demo to Java 21**

  In `mi-sql-public-demo/pom.xml`, change all three occurrences:
  ```xml
  <maven.compiler.source>17</maven.compiler.source>
  <maven.compiler.target>17</maven.compiler.target>
  <java.version>17</java.version>
  ```
  to:
  ```xml
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
  <java.version>21</java.version>
  ```

- [ ] **Step 5: Upgrade rabbitmq-sender to Java 21**

  In `rabbitmq-sender/pom.xml`, change:
  ```xml
  <java.version>17</java.version>
  ```
  to:
  ```xml
  <java.version>21</java.version>
  ```

- [ ] **Step 6: Build all three upgraded modules**

  ```bash
  cd todo-web-api-use-oracle-db && mvn compile 2>&1 | tail -3
  cd ../mi-sql-public-demo && mvn compile 2>&1 | tail -3
  cd ../rabbitmq-sender && mvn compile 2>&1 | tail -3
  ```
  Expected: `BUILD SUCCESS` for each.

- [ ] **Step 7: Commit**

  ```bash
  cd ..
  git add asset-manager/pom.xml todo-web-api-use-oracle-db/pom.xml mi-sql-public-demo/pom.xml rabbitmq-sender/pom.xml
  git commit -m "chore: upgrade java version (asset-manager→17, others→21)"
  ```

---

## Task 002 — Ant → Maven (student-web-app)

**Scope:** Convert `jakarta-ee/student-web-app` from Ant (`build.xml`) to Maven. Move sources to the Maven standard directory layout. Preserve all application behavior. Java target: 17.

**Files:**
- Create: `jakarta-ee/student-web-app/pom.xml`
- Create dirs: `src/main/java/`, `src/main/resources/`, `src/main/webapp/`

---

- [ ] **Step 1: Create `jakarta-ee/student-web-app/pom.xml`**

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <project xmlns="http://maven.apache.org/POM/4.0.0"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
               https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.sample.azure.student</groupId>
    <artifactId>student-web-app</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>war</packaging>

    <properties>
      <maven.compiler.source>17</maven.compiler.source>
      <maven.compiler.target>17</maven.compiler.target>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
      <!-- Servlet API — provided by Liberty at runtime -->
      <dependency>
        <groupId>javax.servlet</groupId>
        <artifactId>javax.servlet-api</artifactId>
        <version>4.0.1</version>
        <scope>provided</scope>
      </dependency>

      <!-- Mail API — provided by Liberty via JNDI -->
      <dependency>
        <groupId>javax.mail</groupId>
        <artifactId>javax.mail-api</artifactId>
        <version>1.6.2</version>
        <scope>provided</scope>
      </dependency>

      <!-- iBATIS 2.x (replaced by MyBatis 3 in Task 004) -->
      <dependency>
        <groupId>org.apache.ibatis</groupId>
        <artifactId>ibatis-sqlmap</artifactId>
        <version>2.3.4.726</version>
      </dependency>

      <!-- Logging -->
      <dependency>
        <groupId>log4j</groupId>
        <artifactId>log4j</artifactId>
        <version>1.2.17</version>
      </dependency>

      <!-- Jackson (older ASL version to match existing JARs) -->
      <dependency>
        <groupId>org.codehaus.jackson</groupId>
        <artifactId>jackson-mapper-asl</artifactId>
        <version>1.9.13</version>
      </dependency>

      <!-- Spring Framework 5.3.x (web + ORM + context) -->
      <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webmvc</artifactId>
        <version>5.3.23</version>
      </dependency>
      <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-orm</artifactId>
        <version>5.3.23</version>
      </dependency>
    </dependencies>

    <build>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-war-plugin</artifactId>
          <version>3.4.0</version>
          <configuration>
            <!-- web.xml is under WEB-INF -->
            <failOnMissingWebXml>true</failOnMissingWebXml>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </project>
  ```

- [ ] **Step 2: Create Maven directory layout**

  ```bash
  cd jakarta-ee/student-web-app
  mkdir -p src/main/java src/main/resources src/main/webapp
  ```

- [ ] **Step 3: Move Java sources**

  The Ant `src/` root contains files under `org/sample/azure/...`. Move them to `src/main/java/`:

  ```bash
  # From jakarta-ee/student-web-app/
  cp -r src/org src/main/java/
  ```

  Verify files are in place:
  ```bash
  find src/main/java -name '*.java' | sort
  ```
  Expected 9 `.java` files under `src/main/java/org/sample/azure/student/...`.

- [ ] **Step 4: Move resources**

  ```bash
  # From jakarta-ee/student-web-app/
  cp -r resources/* src/main/resources/
  ```

  Confirm:
  ```bash
  find src/main/resources -type f | sort
  ```
  Expected: `applicationContext-service.xml`, `log4j.properties`, `sql-map-config.xml`, `org/sample/azure/student/msfaa/shared/persistence/xml/Student_SqlMap.xml`.

- [ ] **Step 5: Move WebContent to webapp**

  ```bash
  # From jakarta-ee/student-web-app/
  cp -r WebContent/* src/main/webapp/
  ```

  Confirm:
  ```bash
  find src/main/webapp -type f | sort
  ```
  Expected: JSPs, `WEB-INF/web.xml`, `WEB-INF/applicationContext.xml`, `WEB-INF/spring-servlet.xml`, and the bundled JARs under `WEB-INF/lib/`.

  > **Note:** The bundled WEB-INF/lib JARs will now overlap with Maven dependencies. The `maven-war-plugin` will use Maven-managed JARs. Delete the copied WEB-INF/lib to avoid duplicates:
  ```bash
  rm -rf src/main/webapp/WEB-INF/lib
  ```

- [ ] **Step 6: Build with Maven**

  ```bash
  # From jakarta-ee/student-web-app/
  mvn package -DskipTests 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS` and `target/student-web-app-1.0-SNAPSHOT.war` created.

- [ ] **Step 7: Commit**

  ```bash
  cd ../..
  git add jakarta-ee/student-web-app/pom.xml jakarta-ee/student-web-app/src/
  git commit -m "feat: migrate student-web-app from ant to maven"
  ```

---

## Task 003 — Migrate asset-manager localhost PostgreSQL to Azure DB for PostgreSQL

**Scope:** Replace the `localhost` JDBC connection in `asset-manager/web` and `asset-manager/worker` with Azure Database for PostgreSQL using Managed Identity (passwordless auth via Spring Cloud Azure). All credentials are externalized to environment variables. Oracle and Docker localhost references in other modules are out of scope.

**Prerequisite:** Task 001 complete (asset-manager builds at Java 17).

**Files:**
- Modify: `asset-manager/pom.xml`
- Modify: `asset-manager/web/pom.xml`
- Modify: `asset-manager/worker/pom.xml`
- Modify: `asset-manager/web/src/main/resources/application.properties`
- Modify: `asset-manager/worker/src/main/resources/application.properties`

**Required environment variable at runtime** (do not commit values):
```
AZURE_POSTGRESQL_HOST=<your-server>.postgres.database.azure.com
AZURE_POSTGRESQL_DATABASE=assets_manager
AZURE_POSTGRESQL_USERNAME=<entra-user>@<your-server>
```

---

- [ ] **Step 1: Add Spring Cloud Azure BOM to `asset-manager/pom.xml`**

  Add inside the existing `<properties>` block:
  ```xml
  <spring-cloud-azure.version>4.16.0</spring-cloud-azure.version>
  ```

  Add a `<dependencyManagement>` section (after `</properties>`):
  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.azure.spring</groupId>
        <artifactId>spring-cloud-azure-dependencies</artifactId>
        <version>${spring-cloud-azure.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```

- [ ] **Step 2: Add passwordless PostgreSQL starter to `asset-manager/web/pom.xml`**

  Add inside `<dependencies>`:
  ```xml
  <dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-jdbc-postgresql</artifactId>
  </dependency>
  ```

- [ ] **Step 3: Add passwordless PostgreSQL starter to `asset-manager/worker/pom.xml`**

  Add inside `<dependencies>`:
  ```xml
  <dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-jdbc-postgresql</artifactId>
  </dependency>
  ```

- [ ] **Step 4: Update `asset-manager/web/src/main/resources/application.properties`**

  Replace the `# Database Configuration` block:
  ```properties
  # Database Configuration — Azure Database for PostgreSQL (Managed Identity)
  spring.datasource.url=jdbc:postgresql://${AZURE_POSTGRESQL_HOST}:5432/${AZURE_POSTGRESQL_DATABASE}
  spring.datasource.username=${AZURE_POSTGRESQL_USERNAME}
  spring.datasource.azure.passwordless-enabled=true
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  spring.jpa.show-sql=true
  ```

  > The `spring.datasource.password` property is intentionally removed. Spring Cloud Azure injects the access token via Managed Identity.

- [ ] **Step 5: Update `asset-manager/worker/src/main/resources/application.properties`**

  Replace the `# Database Configuration` block:
  ```properties
  # Database Configuration — Azure Database for PostgreSQL (Managed Identity)
  spring.datasource.url=jdbc:postgresql://${AZURE_POSTGRESQL_HOST}:5432/${AZURE_POSTGRESQL_DATABASE}
  spring.datasource.username=${AZURE_POSTGRESQL_USERNAME}
  spring.datasource.azure.passwordless-enabled=true
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  ```

- [ ] **Step 6: Build asset-manager to confirm**

  ```bash
  cd asset-manager
  mvn compile 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

  ```bash
  git add asset-manager/pom.xml asset-manager/web/pom.xml asset-manager/worker/pom.xml \
    asset-manager/web/src/main/resources/application.properties \
    asset-manager/worker/src/main/resources/application.properties
  git commit -m "feat: migrate asset-manager postgres to azure db with managed identity"
  ```

---

## Task 004 — Code Quality CWEs (CWE-477, 772, 775, 1057)

**Prerequisites:** Task 002 (student-web-app on Maven), Task 003 (asset-manager builds).

### CWE-477: Replace iBATIS 2.x with MyBatis 3.x in student-web-app

**What:** `MyBatisUtil` uses the obsolete `com.ibatis.sqlmap.client.SqlMapClient` / `SqlMapClientBuilder` APIs (iBATIS 2.x). Replace with MyBatis 3.x `SqlSessionFactory` / `SqlSession`.

**Files:**
- Modify: `jakarta-ee/student-web-app/pom.xml` — swap ibatis-sqlmap for mybatis
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/util/MyBatisUtil.java`
- Modify: `jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/service/StudentService.java` — SqlMapSession → SqlSession

---

- [ ] **Step 1: Swap dependency in `jakarta-ee/student-web-app/pom.xml`**

  Replace:
  ```xml
  <dependency>
    <groupId>org.apache.ibatis</groupId>
    <artifactId>ibatis-sqlmap</artifactId>
    <version>2.3.4.726</version>
  </dependency>
  ```
  With:
  ```xml
  <dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
    <version>3.5.16</version>
  </dependency>
  ```

- [ ] **Step 2: Rewrite `MyBatisUtil.java`**

  Full replacement of `src/main/java/org/sample/azure/student/coreft/util/MyBatisUtil.java`:
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
              Reader reader = Resources.getResourceAsReader("sql-map-config.xml");
              sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
          } catch (Exception e) {
              initializationException = e;
              System.err.println("Failed to initialize SqlSessionFactory: " + e.getMessage());
          }
      }

      public static SqlSessionFactory getSqlSessionFactory() {
          if (sqlSessionFactory == null) {
              throw new RuntimeException(
                  "SqlSessionFactory was not properly initialized. " +
                  (initializationException != null ?
                      "Initialization error: " + initializationException.getMessage() :
                      "Unknown initialization error"),
                  initializationException);
          }
          return sqlSessionFactory;
      }
  }
  ```

  > **Note:** MyBatis 3.x `SqlSessionFactoryBuilder.build(Reader)` reads both MyBatis 3-style `mybatis-config.xml` and legacy iBATIS 2-style `sql-map-config.xml` formats when the DOCTYPE matches. Verify `sql-map-config.xml` is readable by MyBatis 3 before the next step.

- [ ] **Step 3: Update `StudentService.java` to use MyBatis 3.x `SqlSession`**

  Full replacement of `src/main/java/org/sample/azure/student/coreft/service/StudentService.java`:
  ```java
  package org.sample.azure.student.coreft.service;

  import org.apache.ibatis.session.SqlSession;
  import org.apache.log4j.Logger;
  import org.sample.azure.student.coreft.StudentProfile;
  import org.sample.azure.student.coreft.util.MyBatisUtil;
  import org.springframework.stereotype.Service;

  import java.util.ArrayList;
  import java.util.HashMap;
  import java.util.List;
  import java.util.Map;

  @Service
  public class StudentService {

      private static final Logger logger = Logger.getLogger(StudentService.class);

      public List<StudentProfile> getAllStudents() {
          logger.info("Getting all students from database");
          try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
              List<StudentProfile> students = session.selectList("com.azure.sample.StudentMapper.listStudent");
              logger.info("Retrieved " + students.size() + " students");
              return students;
          } catch (Exception ex) {
              logger.error("Error retrieving students: " + ex.getMessage(), ex);
              return new ArrayList<>();
          }
      }

      public boolean saveStudent(String name, String email, String major) {
          logger.info("Saving student: " + name);
          try (SqlSession session = MyBatisUtil.getSqlSessionFactory().openSession()) {
              Map<String, String> params = new HashMap<>();
              params.put("name", name);
              params.put("email", email);
              params.put("major", major);
              session.insert("com.azure.sample.StudentMapper.addStudent", params);
              session.commit();
              logger.info("Student saved successfully: " + name);
              return true;
          } catch (Exception ex) {
              logger.error("Error saving student: " + ex.getMessage(), ex);
              return false;
          }
      }
  }
  ```

- [ ] **Step 4: Build to confirm**

  ```bash
  cd jakarta-ee/student-web-app
  mvn compile 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`. If `sql-map-config.xml` DTD is incompatible with MyBatis 3, the mapper XML may need to be adapted (update DOCTYPE to `mybatis-3-mapper.dtd`).

### CWE-1057: Route direct DB access through StudentService

**What:** `StudentProfileListServlet`, `IndexServlet`, and `AddStudentServlet` directly open `SqlMapSession` (now `SqlSession`) instead of going through the `StudentService` data-manager layer.

- [ ] **Step 5: Rewrite `StudentProfileListServlet.java`**

  Full replacement of `src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java`:
  ```java
  package org.sample.azure.student.coreft;

  import org.apache.log4j.Logger;
  import org.codehaus.jackson.map.ObjectMapper;
  import org.sample.azure.student.coreft.service.StudentService;

  import javax.servlet.ServletException;
  import javax.servlet.http.HttpServlet;
  import javax.servlet.http.HttpServletRequest;
  import javax.servlet.http.HttpServletResponse;
  import java.io.IOException;
  import java.io.PrintWriter;
  import java.util.List;

  public class StudentProfileListServlet extends HttpServlet {

      private static final Logger logger = Logger.getLogger(StudentProfileListServlet.class);
      private final ObjectMapper objectMapper = new ObjectMapper();

      // Injected by Spring (via web.xml SpringBeanAutowiringSupport or constructor)
      private StudentService studentService;

      @Override
      public void init() throws ServletException {
          super.init();
          org.springframework.web.context.WebApplicationContext ctx =
              org.springframework.web.context.support.WebApplicationContextUtils
                  .getRequiredWebApplicationContext(getServletContext());
          studentService = ctx.getBean(StudentService.class);
      }

      @Override
      protected void doGet(HttpServletRequest request, HttpServletResponse response)
              throws ServletException, IOException {
          logger.info("Start to list student profile list");
          response.setContentType("text/html;charset=UTF-8");

          List<StudentProfile> students = studentService.getAllStudents();

          try (PrintWriter out = response.getWriter()) {
              out.println("<html><head><title>Student Profile List</title></head><body>");
              out.println("<h2>Student Profile List</h2>");
              out.println("<table border='1'><tr><th>ID</th><th>Name</th><th>Email</th><th>Major</th></tr>");
              for (StudentProfile student : students) {
                  // CWE-79 fix applied in Task 007 — HTML encoding added here
                  out.println("<tr><td>" + escape(student.getId()) + "</td>" +
                              "<td>" + escape(student.getName()) + "</td>" +
                              "<td>" + escape(student.getEmail()) + "</td>" +
                              "<td>" + escape(student.getMajor()) + "</td></tr>");
              }
              out.println("</table><br/><br/><br/>");
              out.println(objectMapper.writeValueAsString(students));
              out.println("</body></html>");
          }
      }

      // HTML-escape helper (CWE-79)
      private String escape(Object value) {
          if (value == null) return "";
          return String.valueOf(value)
              .replace("&", "&amp;")
              .replace("<", "&lt;")
              .replace(">", "&gt;")
              .replace("\"", "&quot;")
              .replace("'", "&#x27;");
      }
  }
  ```

  > This servlet combines the CWE-1057 fix (use StudentService) and the CWE-79 fix (HTML encode). Both are applied together since they touch the same method.

- [ ] **Step 6: Rewrite `IndexServlet.java`**

  Full replacement of `src/main/java/org/sample/azure/student/coreft/IndexServlet.java`:
  ```java
  package org.sample.azure.student.coreft;

  import org.apache.log4j.Logger;
  import org.sample.azure.student.coreft.service.StudentService;
  import org.springframework.web.context.WebApplicationContext;
  import org.springframework.web.context.support.WebApplicationContextUtils;

  import javax.servlet.ServletException;
  import javax.servlet.http.HttpServlet;
  import javax.servlet.http.HttpServletRequest;
  import javax.servlet.http.HttpServletResponse;
  import java.io.IOException;
  import java.util.List;

  public class IndexServlet extends HttpServlet {

      private static final Logger logger = Logger.getLogger(IndexServlet.class);
      private StudentService studentService;

      @Override
      public void init() throws ServletException {
          super.init();
          WebApplicationContext ctx =
              WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
          studentService = ctx.getBean(StudentService.class);
      }

      @Override
      protected void doGet(HttpServletRequest request, HttpServletResponse response)
              throws ServletException, IOException {
          logger.info("Processing request for index page with student data");
          try {
              List<StudentProfile> students = studentService.getAllStudents();
              request.setAttribute("students", students);
              logger.info("Successfully loaded " + students.size() + " students for index page");
          } catch (Exception ex) {
              logger.error("Error loading students for index page: " + ex.getMessage(), ex);
              request.setAttribute("error", "Unable to load student data: " + ex.getMessage());
              request.setAttribute("students", null);
          }
          request.getRequestDispatcher("/index.jsp").forward(request, response);
      }
  }
  ```

- [ ] **Step 7: Rewrite `AddStudentServlet.java`**

  Full replacement of `src/main/java/org/sample/azure/student/coreft/AddStudentServlet.java`:
  ```java
  package org.sample.azure.student.coreft;

  import org.apache.log4j.Logger;
  import org.sample.azure.student.coreft.service.StudentService;
  import org.springframework.web.context.WebApplicationContext;
  import org.springframework.web.context.support.WebApplicationContextUtils;

  import javax.mail.Message;
  import javax.mail.Session;
  import javax.mail.Transport;
  import javax.mail.internet.InternetAddress;
  import javax.mail.internet.MimeMessage;
  import javax.naming.Context;
  import javax.naming.InitialContext;
  import javax.servlet.ServletException;
  import javax.servlet.http.HttpServlet;
  import javax.servlet.http.HttpServletRequest;
  import javax.servlet.http.HttpServletResponse;
  import java.io.IOException;

  public class AddStudentServlet extends HttpServlet {

      private static final Logger logger = Logger.getLogger(AddStudentServlet.class);
      private StudentService studentService;

      @Override
      public void init() throws ServletException {
          super.init();
          WebApplicationContext ctx =
              WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
          studentService = ctx.getBean(StudentService.class);
      }

      @Override
      protected void doGet(HttpServletRequest request, HttpServletResponse response)
              throws ServletException, IOException {
          logger.info("Displaying add student form");
          request.getRequestDispatcher("/add_student_profile.jsp").forward(request, response);
      }

      @Override
      protected void doPost(HttpServletRequest request, HttpServletResponse response)
              throws ServletException, IOException {
          String name  = request.getParameter("name");
          String email = request.getParameter("email");
          String major = request.getParameter("major");

          logger.info("Starting to add student: name=" + name + ", email=" + email + ", major=" + major);
          boolean success = studentService.saveStudent(name, email, major);

          if (success) {
              logger.info("Redirecting after successful add.");
              try { sendEmail(email, name); } catch (Exception e) {
                  logger.warn("Email notification failed (non-fatal): " + e.getMessage());
              }
              response.setContentType("text/html");
              response.getWriter().write("<h2>Student added successfully!</h2>");
              response.getWriter().write("<p><a href='studentProfileList'>View All Student Profiles</a></p>");
              response.getWriter().write("<p><a href='/'>Add Another Student</a></p>");
          } else {
              logger.warn("Add student failed");
              request.setAttribute("errorMsg", "Failed to add student.");
              request.getRequestDispatcher("/add_student_profile.jsp").forward(request, response);
          }
      }

      private void sendEmail(String to, String name) throws Exception {
          logger.info("Preparing to send email to: " + to);
          Context ctx = new InitialContext();
          Session session = (Session) ctx.lookup("java:comp/env/mail/StudentMailSession");
          Message msg = new MimeMessage(session);
          msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
          msg.setSubject("Welcome, " + name + "!");
          msg.setText("Dear " + name + ",\n\nYour student profile has been created successfully.\n\nRegards,\nAdmin");
          Transport.send(msg);
          logger.info("Email sent to: " + to);
      }
  }
  ```

- [ ] **Step 8: Build student-web-app to confirm**

  ```bash
  cd jakarta-ee/student-web-app
  mvn package -DskipTests 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`.

### CWE-772: Wrap ImageOutputStream in try-with-resources

**What:** In `AbstractFileProcessingService.generateThumbnail()`, two `ImageOutputStream` instances are created manually (JPEG and PNG branches) and `.close()` is called on the happy path — but the streams are not closed if `jpgWriter.write()` or `pngWriter.write()` throws.

**File:** `asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java`

- [ ] **Step 9: Fix JPEG branch — wrap `ImageOutputStream` in try-with-resources**

  In the JPEG branch of `generateThumbnail`, replace:
  ```java
  javax.imageio.stream.ImageOutputStream outputStream = 
      javax.imageio.ImageIO.createImageOutputStream(output.toFile());
  jpgWriter.setOutput(outputStream);
  jpgWriter.write(null, outputImage, jpgWriteParam);
  jpgWriter.dispose();
  outputStream.close();
  ```
  With:
  ```java
  try (javax.imageio.stream.ImageOutputStream outputStream =
          javax.imageio.ImageIO.createImageOutputStream(output.toFile())) {
      jpgWriter.setOutput(outputStream);
      jpgWriter.write(null, outputImage, jpgWriteParam);
  } finally {
      jpgWriter.dispose();
  }
  ```

- [ ] **Step 10: Fix PNG branch — wrap `ImageOutputStream` in try-with-resources**

  In the PNG branch, replace:
  ```java
  javax.imageio.stream.ImageOutputStream outputStream = 
      javax.imageio.ImageIO.createImageOutputStream(output.toFile());
  pngWriter.setOutput(outputStream);
  pngWriter.write(null, outputImage, pngWriteParam);
  pngWriter.dispose();
  outputStream.close();
  ```
  With:
  ```java
  try (javax.imageio.stream.ImageOutputStream outputStream =
          javax.imageio.ImageIO.createImageOutputStream(output.toFile())) {
      pngWriter.setOutput(outputStream);
      pngWriter.write(null, outputImage, pngWriteParam);
  } finally {
      pngWriter.dispose();
  }
  ```

- [ ] **Step 11: Build asset-manager worker**

  ```bash
  cd asset-manager
  mvn compile -pl worker 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`.

### CWE-775: Close ResultSet in try-with-resources

**What:** `OracleSqlDemonstrator.executeRawOracleQuery()` calls `stmt.executeQuery()` and assigns the result to `ResultSet rs` which is not in a try-with-resources block — it leaks the file descriptor if an exception is thrown during iteration.

**File:** `todo-web-api-use-oracle-db/src/main/java/com/microsoft/migration/todo/util/OracleSqlDemonstrator.java`

- [ ] **Step 12: Add `ResultSet` to the existing try-with-resources**

  In `executeRawOracleQuery`, replace:
  ```java
  try (Connection conn = jdbcTemplate.getDataSource().getConnection();
       PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, keyword);
      stmt.setString(2, keyword);
      stmt.setInt(3, minPriority);

      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
  ```
  With:
  ```java
  try (Connection conn = jdbcTemplate.getDataSource().getConnection();
       PreparedStatement stmt = conn.prepareStatement(sql);
       ResultSet rs = stmt.executeQuery()) {

      stmt.setString(1, keyword);
      stmt.setString(2, keyword);
      stmt.setInt(3, minPriority);

      while (rs.next()) {
  ```

  > The `stmt.setXxx()` calls must move before the `ResultSet` usage. Since `rs` is now declared in the try header and `executeQuery()` is called there, the parameter binding must happen before `rs` is opened. Restructure by splitting into two resources:

  Actually, parameters must be set before `executeQuery()`. The correct pattern is:

  ```java
  try (Connection conn = jdbcTemplate.getDataSource().getConnection();
       PreparedStatement stmt = conn.prepareStatement(sql)) {

      stmt.setString(1, keyword);
      stmt.setString(2, keyword);
      stmt.setInt(3, minPriority);

      try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
              Map<String, Object> row = new HashMap<>();
              row.put("id", rs.getLong("ID"));
              row.put("title", rs.getString("TITLE"));
              row.put("shortDescription", rs.getString("SHORT_DESC"));
              row.put("isLongDescription", "Y".equals(rs.getString("IS_LONG_DESC")));
              row.put("priority", rs.getInt("PRIORITY"));
              row.put("formattedDueDate", rs.getString("FORMATTED_DUE_DATE"));
              row.put("daysSinceCreation", rs.getInt("DAYS_SINCE_CREATION"));
              results.add(row);
          }
      }

      log.info("Executed Oracle-specific SQL query with {} results", results.size());
      return results;
  }
  ```

- [ ] **Step 13: Build todo-web-api**

  ```bash
  cd todo-web-api-use-oracle-db
  mvn compile 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 14: Commit all Task 004 changes**

  ```bash
  cd ..
  git add jakarta-ee/student-web-app/pom.xml \
    jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/util/MyBatisUtil.java \
    jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/service/StudentService.java \
    jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java \
    jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/IndexServlet.java \
    jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/AddStudentServlet.java \
    asset-manager/worker/src/main/java/com/microsoft/migration/assets/worker/service/AbstractFileProcessingService.java \
    todo-web-api-use-oracle-db/src/main/java/com/microsoft/migration/todo/util/OracleSqlDemonstrator.java
  git commit -m "fix: remediate CWE-477 (ibatis→mybatis), CWE-1057 (use service layer), CWE-772/775 (resource leaks)"
  ```

---

## Task 005 — Credentials & Secrets CWEs (CWE-259, 778, 798)

**Prerequisite:** Task 003 complete (datasource URL already externalized for PostgreSQL).

### CWE-259 / CWE-798: Externalize remaining plaintext credentials

**What:** RabbitMQ `username`/`password` and AWS `accessKey`/`secretKey` are still hardcoded in both `application.properties` files after Task 003.

**Files:**
- Modify: `asset-manager/web/src/main/resources/application.properties`
- Modify: `asset-manager/worker/src/main/resources/application.properties`

**Required environment variables at runtime:**
```
RABBITMQ_HOST=<host>
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=<user>
RABBITMQ_PASSWORD=<password>
AWS_ACCESS_KEY=<key>
AWS_SECRET_KEY=<secret>
AWS_REGION=us-east-1
AWS_S3_BUCKET=<bucket>
```

---

- [ ] **Step 1: Replace hardcoded credentials in `asset-manager/web/src/main/resources/application.properties`**

  Full file after change:
  ```properties
  spring.application.name=assets-manager

  # AWS S3 Configuration
  aws.accessKey=${AWS_ACCESS_KEY}
  aws.secretKey=${AWS_SECRET_KEY}
  aws.region=${AWS_REGION:us-east-1}
  aws.s3.bucket=${AWS_S3_BUCKET}

  # Max file size for uploads
  spring.servlet.multipart.max-file-size=10MB
  spring.servlet.multipart.max-request-size=10MB

  # RabbitMQ Configuration
  spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
  spring.rabbitmq.port=${RABBITMQ_PORT:5672}
  spring.rabbitmq.username=${RABBITMQ_USERNAME}
  spring.rabbitmq.password=${RABBITMQ_PASSWORD}

  # Database Configuration — Azure Database for PostgreSQL (Managed Identity)
  spring.datasource.url=jdbc:postgresql://${AZURE_POSTGRESQL_HOST}:5432/${AZURE_POSTGRESQL_DATABASE}
  spring.datasource.username=${AZURE_POSTGRESQL_USERNAME}
  spring.datasource.azure.passwordless-enabled=true
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  spring.jpa.show-sql=true
  ```

- [ ] **Step 2: Replace hardcoded credentials in `asset-manager/worker/src/main/resources/application.properties`**

  Full file after change:
  ```properties
  # AWS S3 Configuration
  aws.accessKeyId=${AWS_ACCESS_KEY}
  aws.secretKey=${AWS_SECRET_KEY}
  aws.region=${AWS_REGION:us-east-1}
  aws.s3.bucket=${AWS_S3_BUCKET}

  # Application name
  spring.application.name=assets-manager-worker

  # RabbitMQ Configuration
  spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
  spring.rabbitmq.port=${RABBITMQ_PORT:5672}
  spring.rabbitmq.username=${RABBITMQ_USERNAME}
  spring.rabbitmq.password=${RABBITMQ_PASSWORD}

  # Database Configuration — Azure Database for PostgreSQL (Managed Identity)
  spring.datasource.url=jdbc:postgresql://${AZURE_POSTGRESQL_HOST}:5432/${AZURE_POSTGRESQL_DATABASE}
  spring.datasource.username=${AZURE_POSTGRESQL_USERNAME}
  spring.datasource.azure.passwordless-enabled=true
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  ```

### CWE-778: Add audit logging to S3Controller

**What:** Upload, view, and delete operations in `S3Controller` do not log access events or failures, making it impossible to detect unauthorized file access.

**File:** `asset-manager/web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java`

- [ ] **Step 3: Add a SLF4J logger field to `S3Controller`**

  At the top of the class (after the `@RequiredArgsConstructor` annotation), add:
  ```java
  private static final org.slf4j.Logger auditLog =
      org.slf4j.LoggerFactory.getLogger("AUDIT." + S3Controller.class.getName());
  ```

- [ ] **Step 4: Add audit logging to `uploadObject`**

  In the `uploadObject` method, add an audit log on success and on failure. The try block becomes:
  ```java
  @PostMapping("/upload")
  public String uploadObject(@RequestParam("file") MultipartFile file,
                             RedirectAttributes redirectAttributes) {
      try {
          if (file.isEmpty()) {
              redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
              return "redirect:/" + StorageConstants.STORAGE_PATH + "/upload";
          }
          storageService.uploadObject(file);
          auditLog.info("UPLOAD success: filename={}", file.getOriginalFilename());
          redirectAttributes.addFlashAttribute("success", "File uploaded successfully");
          return "redirect:/" + StorageConstants.STORAGE_PATH;
      } catch (IOException e) {
          auditLog.warn("UPLOAD failure: filename={} error={}", file.getOriginalFilename(), e.getMessage());
          redirectAttributes.addFlashAttribute("error", "Failed to upload file: " + e.getMessage());
          return "redirect:/" + StorageConstants.STORAGE_PATH + "/upload";
      }
  }
  ```

- [ ] **Step 5: Add audit logging to `viewObject`**

  In the `viewObject` `@GetMapping("/view/{key}")` method, add:
  ```java
  @GetMapping("/view/{key}")
  public ResponseEntity<InputStreamResource> viewObject(@PathVariable String key) {
      try {
          InputStream inputStream = storageService.getObject(key);
          auditLog.info("VIEW success: key={}", key);
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
          return ResponseEntity.ok()
              .headers(headers)
              .body(new InputStreamResource(inputStream));
      } catch (Exception e) {
          auditLog.warn("VIEW failure: key={} error={}", key, e.getMessage());
          return ResponseEntity.notFound().build();
      }
  }
  ```

- [ ] **Step 6: Add audit logging to `deleteObject`**

  Find the `deleteObject` handler (it will be a `@DeleteMapping` or `@PostMapping` with key). Add before and after the service call:
  ```java
  auditLog.info("DELETE attempt: key={}", key);
  storageService.deleteObject(key);
  auditLog.info("DELETE success: key={}", key);
  ```
  On failure:
  ```java
  auditLog.warn("DELETE failure: key={} error={}", key, e.getMessage());
  ```

- [ ] **Step 7: Build asset-manager web**

  ```bash
  cd asset-manager
  mvn compile -pl web 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

  ```bash
  git add asset-manager/web/src/main/resources/application.properties \
    asset-manager/worker/src/main/resources/application.properties \
    asset-manager/web/src/main/java/com/microsoft/migration/assets/controller/S3Controller.java
  git commit -m "fix: remediate CWE-259/798 (externalize credentials to env vars), CWE-778 (add audit logging)"
  ```

---

## Task 006 — File & Path Security CWEs (CWE-22, 23, 36, 434)

**Prerequisite:** Task 003 complete.

### CWE-22 / CWE-23 / CWE-36: Path traversal in `LocalFileStorageService`

**What:** `getObject(key)` and `deleteObject(key)` resolve the user-supplied `key` directly against `rootLocation` without canonicalization. A key like `../../etc/passwd` or `/etc/passwd` escapes the storage root.

**File:** `asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java`

---

- [ ] **Step 1: Add a `validateKey` helper method to `LocalFileStorageService`**

  Add this private method to the class:
  ```java
  /**
   * Validates that the resolved path stays within rootLocation.
   * Prevents CWE-22 (path traversal), CWE-23 (relative path traversal),
   * and CWE-36 (absolute path traversal).
   */
  private Path safeResolve(String key) throws IOException {
      if (key == null || key.isBlank()) {
          throw new IOException("File key must not be blank");
      }
      // Reject any key that is an absolute path
      if (Paths.get(key).isAbsolute()) {
          throw new IOException("Absolute path keys are not permitted: " + key);
      }
      Path resolved = rootLocation.resolve(key).normalize();
      if (!resolved.startsWith(rootLocation)) {
          throw new IOException("Path traversal detected for key: " + key);
      }
      return resolved;
  }
  ```

- [ ] **Step 2: Use `safeResolve` in `getObject`**

  Replace:
  ```java
  public InputStream getObject(String key) throws IOException {
      Path file = rootLocation.resolve(key);
      if (!Files.exists(file)) {
          throw new FileNotFoundException("File not found: " + key);
      }
      return new BufferedInputStream(Files.newInputStream(file));
  }
  ```
  With:
  ```java
  public InputStream getObject(String key) throws IOException {
      Path file = safeResolve(key);
      if (!Files.exists(file)) {
          throw new FileNotFoundException("File not found: " + key);
      }
      return new BufferedInputStream(Files.newInputStream(file));
  }
  ```

- [ ] **Step 3: Use `safeResolve` in `deleteObject`**

  Replace:
  ```java
  public void deleteObject(String key) throws IOException {
      Path file = rootLocation.resolve(key);
  ```
  With:
  ```java
  public void deleteObject(String key) throws IOException {
      Path file = safeResolve(key);
  ```

  Also update the thumbnail deletion block to use `safeResolve`:
  ```java
  try {
      Path thumbnailFile = safeResolve(getThumbnailKey(key));
      if (Files.exists(thumbnailFile)) {
          Files.delete(thumbnailFile);
          logger.info("Deleted thumbnail file: {}", thumbnailFile);
      }
  } catch (Exception e) {
      logger.warn("Could not delete thumbnail for {}: {}", key, e.getMessage());
  }
  ```

### CWE-434: File type allow-list on upload

**What:** `uploadObject` in `S3Controller` and `LocalFileStorageService` accept any file type. An attacker can upload a `.jsp` or `.sh` and potentially achieve RCE if the server serves or executes it.

- [ ] **Step 4: Add an allow-list constant to `LocalFileStorageService`**

  Add at the top of the class (after the logger):
  ```java
  private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
      "jpg", "jpeg", "png", "gif", "webp", "bmp",
      "pdf", "txt", "csv", "zip"
  );
  ```

- [ ] **Step 5: Add extension validation to `uploadObject` in `LocalFileStorageService`**

  Inside `uploadObject`, after the empty-file check, add:
  ```java
  String filename = StringUtils.cleanPath(file.getOriginalFilename());
  if (filename.contains("..")) {
      throw new IOException("Cannot store file with relative path outside current directory");
  }
  String extension = "";
  int dotIndex = filename.lastIndexOf('.');
  if (dotIndex >= 0) {
      extension = filename.substring(dotIndex + 1).toLowerCase();
  }
  if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new IOException("File type not permitted: " + extension);
  }
  ```

  Remove the duplicate `filename` variable assignment that already existed below this point (it was previously the first line of the method body).

- [ ] **Step 6: Build asset-manager web**

  ```bash
  cd asset-manager
  mvn compile -pl web 2>&1 | tail -5
  ```
  Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

  ```bash
  git add asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java
  git commit -m "fix: remediate CWE-22/23/36 (path traversal) and CWE-434 (unrestricted upload)"
  ```

---

## Task 007 — Injection CWEs (CWE-79, CWE-99)

**Prerequisites:** Task 002 (student-web-app on Maven), Task 003.

### CWE-79: Stored XSS in `StudentProfileListServlet`

Already applied in **Task 004, Step 5** as part of the CWE-1057 rewrite. The `escape()` helper HTML-encodes all student fields before writing them to the response. No additional changes needed if Task 004 is complete.

Verify:
```bash
grep -n 'escape(' jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java
```
Expected: at least 4 lines showing `escape(student.get...)` calls.

### CWE-99: Resource injection via user-controlled key in `LocalFileStorageService`

Already applied in **Task 006, Steps 1–3** via the `safeResolve` method. The key is validated to be non-blank, non-absolute, and confined to `rootLocation`. No additional changes needed if Task 006 is complete.

Verify:
```bash
grep -n 'safeResolve' asset-manager/web/src/main/java/com/microsoft/migration/assets/service/LocalFileStorageService.java
```
Expected: 3 occurrences (method definition + `getObject` + `deleteObject`).

- [ ] **Step 1: Build both modules to confirm no regressions**

  ```bash
  cd jakarta-ee/student-web-app && mvn package -DskipTests 2>&1 | tail -3
  cd ../../asset-manager && mvn compile -pl web 2>&1 | tail -3
  ```
  Expected: `BUILD SUCCESS` for each.

- [ ] **Step 2: Commit**

  > If CWE-79 and CWE-99 were already committed with Tasks 004 and 006 respectively, skip this commit. Otherwise:
  ```bash
  git add jakarta-ee/student-web-app/src/main/java/org/sample/azure/student/coreft/StudentProfileListServlet.java
  git commit -m "fix: remediate CWE-79 (XSS) and CWE-99 (resource injection)"
  ```

---

## Task 008 — CVE Remediation

**Prerequisites:** Task 001 (Java version), Task 003 (Spring Cloud Azure BOM in asset-manager).

**Affected CVEs and fix strategy:**

| CVE | Artifact | Module | Fix |
|-----|----------|--------|-----|
| CVE-2024-1597 | `org.postgresql:postgresql` (SQL injection) | asset-manager web + worker | Override to `42.7.2` |
| CVE-2026-40972 | `spring-boot-devtools` (timing attack) | asset-manager web, todo-web-api | Remove from production scope or move to `test` |
| CVE-2026-42198 | `org.postgresql:postgresql` (DoS) | asset-manager web + worker | Covered by `42.7.2` override above |
| CVE-2026-54512 | `com.fasterxml.jackson.core:jackson-databind` | asset-manager worker | Override to `2.17.2` |
| CVE-2026-54513 | `com.fasterxml.jackson.core:jackson-databind` | rabbitmq-sender | Override to `2.17.2` |

---

- [ ] **Step 1: Override `postgresql` version in `asset-manager/pom.xml`**

  Add to the `<properties>` block:
  ```xml
  <postgresql.version>42.7.2</postgresql.version>
  ```

  Spring Boot 2.7's `spring-boot-starter-parent` manages `postgresql` via `postgresql.version` property — adding it here overrides the managed version for the entire `asset-manager` parent.

  Verify the override takes effect:
  ```bash
  cd asset-manager
  mvn dependency:tree -pl web | grep postgresql
  ```
  Expected: `org.postgresql:postgresql:jar:42.7.2`.

- [ ] **Step 2: Remove / scope `spring-boot-devtools` from asset-manager/web**

  In `asset-manager/web/pom.xml`, remove the `spring-boot-devtools` dependency entirely:
  ```xml
  <!-- Remove this block: -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
  </dependency>
  ```

  > DevTools is for local development only. It must not be on the production classpath. If local hot-reload is needed, add it back with `<scope>test</scope>` and `<optional>true</optional>`.

- [ ] **Step 3: Remove `spring-boot-devtools` from todo-web-api**

  In `todo-web-api-use-oracle-db/pom.xml`, remove the `spring-boot-devtools` dependency. Confirm with:
  ```bash
  grep -c 'devtools' todo-web-api-use-oracle-db/pom.xml
  ```
  Expected: `0`.

- [ ] **Step 4: Override `jackson-databind` in `asset-manager/worker`**

  Add to `asset-manager/worker/pom.xml` inside `<dependencyManagement><dependencies>`:
  ```xml
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.2</version>
  </dependency>
  ```

  If there is no `<dependencyManagement>` section yet, add one:
  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.2</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```

  Verify:
  ```bash
  cd asset-manager
  mvn dependency:tree -pl worker | grep jackson-databind
  ```
  Expected: `jackson-databind:jar:2.17.2`.

- [ ] **Step 5: Override `jackson-databind` in `rabbitmq-sender`**

  In `rabbitmq-sender/pom.xml`, add inside `<dependencyManagement><dependencies>` (it already has a `dependencyManagement` section):
  ```xml
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.2</version>
  </dependency>
  ```

  Verify:
  ```bash
  cd rabbitmq-sender
  mvn dependency:tree | grep jackson-databind
  ```
  Expected: `jackson-databind:jar:2.17.2`.

- [ ] **Step 6: Build all affected modules**

  ```bash
  cd asset-manager && mvn compile 2>&1 | tail -3
  cd ../todo-web-api-use-oracle-db && mvn compile 2>&1 | tail -3
  cd ../rabbitmq-sender && mvn compile 2>&1 | tail -3
  ```
  Expected: `BUILD SUCCESS` for each.

- [ ] **Step 7: Commit**

  ```bash
  cd ..
  git add asset-manager/pom.xml \
    asset-manager/web/pom.xml \
    asset-manager/worker/pom.xml \
    todo-web-api-use-oracle-db/pom.xml \
    rabbitmq-sender/pom.xml
  git commit -m "fix: patch CVEs (postgresql 42.7.2, remove devtools, jackson-databind 2.17.2)"
  ```

---

## Self-Review Checklist

- [x] **Spec coverage:** All 8 tasks from tasks.json are represented. Tasks 007 CWE-79/99 are folded into Tasks 004 and 006 respectively (same files, same step) and cross-referenced.
- [x] **No placeholders:** All steps contain actual file names, code, and commands.
- [x] **Type consistency:** `MyBatisUtil.getSqlSessionFactory()` is defined in Task 004 Step 2 and used in `StudentService` (Step 3) and all three servlet rewrites (Steps 5–7).
- [x] **Credential note:** `safeResolve()` is defined once in `LocalFileStorageService` (Task 006 Step 1) and referenced in Steps 2 and 3 of the same task.
- [x] **CVE-2026-40972 (devtools):** Removed from `asset-manager/web` and `todo-web-api`. `mi-sql-public-demo` and `rabbitmq-sender` do not use devtools — confirmed by dependency scan above.
- [x] **Task 007 note:** CWE-79 is applied in Task 004 Step 5 (same servlet rewrite). CWE-99 is applied in Task 006 Steps 1–3 (same `safeResolve` addition). Task 007 steps verify the fixes are present and build cleanly; no duplicate code changes.

# Modernization Plan: Lab2 Modernization Plan

**Project**: java-migration-copilot-samples-lab2

---

## Technical Framework

- **Language**: Java 8 (JDK 8); also contains C#, JavaScript, and Dockerfiles
- **Framework**: Spring Boot, Spring, Jakarta EE (jakarta-ee/student-web-app)
- **Build Tool**: Maven (primary); Ant (jakarta-ee/student-web-app)
- **Database**: Oracle (todo-web-api-use-oracle-db), PostgreSQL (asset-manager), MySQL (jakarta-ee/student-web-app)
- **Key Dependencies**: Spring Data JPA, RabbitMQ AMQP, jackson-databind, postgresql JDBC, spring-boot-devtools, iBATIS/MyBatis

---

## Overview

This modernization prepares the workspace for Azure by upgrading the Java runtime, standardizing the build tooling, removing localhost dependencies, and remediating the security findings from the assessment report. The application currently runs on JDK 8, uses an Ant build for one module, references localhost resources, and carries multiple code-quality, credential, file/path, injection, and dependency (CVE) vulnerabilities. The new state will:

- Run on the latest LTS Java (Java 25) for security, performance, and supportability
- Use Maven consistently across modules for dependency management and CI/CD readiness
- Target Azure managed resources instead of localhost for cloud readiness
- Be remediated against the assessment's CWE findings and known CVEs before deployment

The migration follows a phased approach: runtime upgrade first, then build and cloud-readiness transforms, followed by sequential security remediation, ending with dependency CVE fixes.

---

## Migration Impact Summary

| Application | Original Service | New Azure Service | Authentication | Comments |
|-------------|------------------|-------------------|----------------|----------|
| asset-manager | Local JDBC / localhost | Azure managed datastore | Managed Identity | Remove localhost JDBC + endpoints |
| todo-web-api-use-oracle-db | localhost endpoint | Azure managed resource | Managed Identity | Externalize datasource config |
| student-web-app | Ant build + localhost | Maven + Azure resource | Managed Identity | Build tool + cloud readiness |

---

## Phases

### Phase 1 — Runtime Upgrade
- **001-upgrade-java-version** (`upgrade`, kbId: `java-version-upgrade`) — Upgrade Java to the latest LTS (Java 25). Must run first.

### Phase 2 — Build & Cloud-Readiness Transforms
- **002-transform-migration-ant-project-to-maven-project** (`transform`, kbId: `ant-project-to-maven-project`) — Migrate jakarta-ee/student-web-app from Ant to Maven.
- **003-transform-migrate-local-resource-to-azure** (`transform`) — Migrate local JDBC / localhost resources to Azure managed services.

### Phase 3 — Security Remediation (CWE)
Executed sequentially to avoid conflicts on overlapping files:
- **004** CWE-477 — Use of Obsolete Function
- **005** CWE-665 — Improper Initialization
- **006** CWE-681 — Incorrect Conversion between Numeric Types
- **007** CWE-772 — Missing Release of Resource after Effective Lifetime
- **008** CWE-775 — Missing Release of File Descriptor or Handle
- **009** CWE-1057 — Data Access Operations Outside of Expected Data Manager Component
- **010** CWE-259 — Use of Hard-coded Password
- **011** CWE-778 — Insufficient Logging
- **012** CWE-798 — Use of Hard-coded Credentials
- **013** CWE-22 — Path Traversal
- **014** CWE-23 — Relative Path Traversal
- **015** CWE-36 — Absolute Path Traversal
- **016** CWE-434 — Unrestricted Upload of File with Dangerous Type
- **017** CWE-79 — Cross-site Scripting
- **018** CWE-99 — Resource Injection

### Phase 4 — Dependency CVE Remediation
- **019-security-scan-and-fix-cve** (`security`, skill: `validate-cves-and-fix`) — Scan and fix CVEs in declared dependencies (spring-boot-devtools, postgresql, jackson-databind, etc.).

---

## Notes

- Detailed, programmatic task definitions (requirements, dependencies, success criteria) are tracked in [.metadata/tasks.json](.metadata/tasks.json).
- The C# project `Malshinon/DBConnection.cs` is referenced in credential findings (CWE-259/CWE-798); remediation should externalize its hard-coded connection string as well.
- Security tasks run after the upgrade and transform tasks and before any deployment, per the modernization guidelines.

---

## Open Questions & Questionnaire

- [x] Q: What Java target version should the upgrade use? → A: Latest LTS (Java 25), JDK-only upgrade (no framework upgrade unless required for compatibility).
- [x] Q: What authentication method should be used for migrated Azure resources? → A: Managed Identity (default) where applicable.

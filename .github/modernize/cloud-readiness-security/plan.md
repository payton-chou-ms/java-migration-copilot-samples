# Modernization Plan: Cloud Readiness & Security Modernization

**Project**: java-migration-copilot-samples

---

## Technical Framework

- **Language**: Java (mixed: Java 8 and Java 17 across modules)
- **Framework**: Spring Boot 2.7.18 (asset-manager), Spring Boot 3.2.4 (todo-web-api), Jakarta EE / Servlets (student-web-app)
- **Build Tool**: Maven (most modules); Apache Ant (jakarta-ee/student-web-app)
- **Database**: Local PostgreSQL (asset-manager), Oracle (todo-web-api), Azure SQL demo (mi-sql-public-demo)
- **Key Dependencies**: Spring Boot DevTools, org.postgresql:postgresql, jackson-databind, iBATIS/MyBatis, AWS S3 SDK

---

## Overview

> This modernization prepares the repository's Java workloads for Azure hosting and hardens them
> against known security weaknesses. The applications currently target outdated JDKs, build one
> module with Ant, depend on local (localhost) resources, and contain multiple code-quality,
> credential, file-path, and injection vulnerabilities along with several mandatory CVEs.
> The modernized state will:
>
> - Run on the latest LTS Java and a standardized Maven build for all modules.
> - Replace local database/localhost resources with managed Azure services using passwordless auth.
> - Remediate the assessed CWE classes (code quality, secrets, file/path, injection) and patch CVEs.
>
> The migration follows a phased approach: runtime/build standardization first, then resource
> migration, then security remediation.

---

## Migration Impact Summary

| Application | Original Service | New Azure Service | Authentication | Comments |
|-------------|------------------|-------------------|----------------|----------|
| asset-manager | Local PostgreSQL (localhost JDBC) | Azure Database for PostgreSQL | Managed Identity | Mandatory cloud-readiness blocker |
| asset-manager | Plaintext creds in config | Environment variables | n/a | CWE-259 / CWE-798; Key Vault deferred |
| student-web-app | Ant build | Maven build | n/a | Build-tool standardization |
| asset-manager / worker | Java 8 (Spring Boot 2.7) | Java 17 | n/a | Spring Boot 2.7 incompatible with Java 21 |
| todo-web-api, mi-sql-public-demo, rabbitmq-sender | Java 17 | Java 21 | n/a | JDK-only upgrade |

---

## Task Summary

The detailed, executable task breakdown lives in [.metadata/tasks.json](.metadata/tasks.json).
The plan contains **8 tasks**: 1 upgrade, 2 transform, and 5 security tasks.

| # | ID | Type | Goal | Skill / kbId |
|---|----|------|------|--------------|
| 1 | 001-upgrade-java-version | upgrade | Upgrade Maven modules: asset-manager → Java 17, others → Java 21 | kbId: java-version-upgrade |
| 2 | 002-transform-migration-ant-project-to-maven-project | transform | Convert student-web-app from Ant to Maven | migration-ant-project-to-maven-project (kbId: ant-project-to-maven-project) |
| 3 | 003-transform-migrate-local-resource-to-azure | transform | Migrate asset-manager localhost PostgreSQL to Azure DB for PostgreSQL (Managed Identity) | migration-mi-postgresql |
| 4 | 004-security-resolve-code-quality-cwes | security | Fix CWE-477, 772, 775, 1057 | — |
| 5 | 005-security-resolve-credentials-and-secrets-cwes | security | Fix CWE-259, 778, 798 | — |
| 6 | 006-security-resolve-file-and-path-cwes | security | Fix CWE-22, 23, 36, 434 | — |
| 7 | 007-security-resolve-injection-cwes | security | Fix CWE-79, 99 | — |
| 8 | 008-security-fix-cve-vulnerabilities | security | Scan and patch CVEs in dependencies | validate-cves-and-fix |

---

## Phases

1. **Phase 1 — Runtime & Build Standardization**: Task 001 (Java upgrade), Task 002 (Ant → Maven).
2. **Phase 2 — Resource Migration**: Task 003 (localhost resources → Azure with Managed Identity).
3. **Phase 3 — Security Remediation**: Tasks 004–007 (CWE classes), Task 008 (CVE patching).

---

## Decisions

- [x] **Java upgrade split**: asset-manager (Spring Boot 2.7) is pinned to **Java 17** — the
  maximum version compatible with Spring Boot 2.7. All other Maven modules (todo-web-api,
  mi-sql-public-demo, rabbitmq-sender) are upgraded to **Java 21**. A future Spring Boot 3.x
  upgrade for asset-manager is out of scope for this plan.
- [x] **Localhost migration scope**: Task 003 covers **asset-manager only** (localhost PostgreSQL →
  Azure Database for PostgreSQL with Managed Identity). The Oracle localhost references in
  todo-web-api and the Docker Compose / Liberty localhost references in student-web-app are out of
  scope and deferred to dedicated migration plans.
- [x] **Secrets management**: Plaintext credentials in asset-manager application.properties are
  replaced with **environment variable** injection (`${ENV_VAR}`). Azure Key Vault integration is
  not required for this plan.

---

## Notes

- No deployment task is included because deployment was not requested.
- Security tasks are ordered after the upgrade and transform tasks, per modernization conventions.
- todo-web-api Oracle localhost references and student-web-app Docker Compose / Liberty localhost
  references are out of scope; deferred to separate migration plans.
- A Spring Boot 3.x upgrade for asset-manager (required to reach Java 21) is out of scope;
  deferred to a future plan.

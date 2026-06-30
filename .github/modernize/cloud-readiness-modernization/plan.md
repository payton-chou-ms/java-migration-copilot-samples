# Modernization Plan: Cloud Readiness Modernization Plan

**Project**: java-migration-copilot-samples-lab2

---

## Technical Framework

- **Language**: Java (also contains C#, JavaScript); target runtime is the latest LTS (Java 25)
- **Framework**: Spring Boot, Spring, Jakarta EE / Open Liberty (jakarta-ee/student-web-app)
- **Build Tool**: Maven
- **Database**: Microsoft SQL (mi-sql-public-demo), Oracle (todo-web-api-use-oracle-db), PostgreSQL (asset-manager), MySQL (jakarta-ee/student-web-app)
- **Key Dependencies**: Spring Data JPA, Spring AMQP (RabbitMQ), AWS S3 SDK, JavaMail, logback / log4j

---

## Overview

This modernization makes the workspace Azure-ready by removing cross-cloud and on-premises dependencies, migrating databases and messaging to Azure managed services, securing credentials, and remediating known vulnerabilities. The application currently runs on a Java version below the latest LTS, stores objects in AWS S3, uses RabbitMQ AMQP messaging, connects to Microsoft SQL, Oracle, PostgreSQL, and MySQL with passwords, references localhost resources, writes logs to the file system, and contains plaintext and hard-coded credentials. The new state will:

- Run on the latest LTS Java (Java 25) for security, performance, and supportability
- Use Azure managed services for storage (Blob Storage), messaging (Service Bus), databases (Azure SQL, PostgreSQL, MySQL), and email (Azure Communication Service)
- Authenticate to Azure data services with Managed Identity instead of stored passwords
- Remove localhost dependencies, plaintext, hard-coded, and default credentials, and switch to console logging
- Be remediated against known high-severity CVEs before deployment

The migration follows a phased approach: runtime upgrade first, then service and cloud-readiness transforms, credential hardening, and finally dependency CVE remediation.

---

## Migration Impact Summary

| Application | Original Service | New Azure Service | Authentication | Comments |
|-------------|------------------|-------------------|----------------|----------|
| asset-manager | AWS S3 | Azure Blob Storage | Managed Identity | Storage migration |
| asset-manager | RabbitMQ (AMQP) | Azure Service Bus | Managed Identity | Messaging migration |
| asset-manager | PostgreSQL (password) | Azure DB for PostgreSQL | Managed Identity | Passwordless auth |
| asset-manager | AWS region config | Azure region config | n/a | Region settings |
| mi-sql-public-demo | Microsoft SQL (password) | Azure SQL Database | Managed Identity | Passwordless auth |
| todo-web-api-use-oracle-db | Oracle Database | Azure DB for PostgreSQL | Managed Identity | DB engine migration |
| student-web-app | MySQL (password) | Azure DB for MySQL | Managed Identity | Passwordless auth |
| student-web-app | Open Liberty datasource | Azure Database Services | Managed Identity | Cloud readiness |
| student-web-app | JavaMail (SMTP) | Azure Communication Service | Managed Identity | Email migration |
| student-web-app | File-system logging | Console logging | n/a | Cloud-native logs |

---

## Phases

### Phase 1 — Runtime Upgrade
- **001-upgrade-java-version** (`upgrade`, kbId: `java-version-upgrade`) — Upgrade Java to the latest LTS (Java 25). Must run first.

### Phase 2 — Service & Cloud-Readiness Transforms
Executed sequentially to avoid conflicts on shared configuration files (e.g. asset-manager application.properties):
- **002-transform-migration-s3-to-azure-blob-storage** (`transform`, kbId: `s3-to-azure-blob-storage`) — Migrate AWS S3 to Azure Blob Storage.
- **003-transform-migration-amqp-rabbitmq-to-servicebus** (`transform`, kbId: `amqp-rabbitmq-servicebus`) — Migrate RabbitMQ (AMQP) to Azure Service Bus.
- **004-transform-mi-azure-sql** (`transform`, kbId: `mi-azure-sql`) — Secure Azure SQL Database with Managed Identity.
- **005-transform-migration-oracle-to-postgresql** (`transform`, kbId: `oracle-to-postgresql`) — Migrate Oracle DB to PostgreSQL.
- **006-transform-mi-postgresql** (`transform`, kbId: `mi-postgresql`) — Secure Azure Database for PostgreSQL with Managed Identity.
- **007-transform-mi-mysql** (`transform`, kbId: `mi-mysql`) — Migrate to Azure Database for MySQL.
- **008-transform-openliberty-database-to-azure** (`transform`) — Migrate Open Liberty datasource references to Azure Database Services.
- **009-transform-javamail-to-azure-communication-service** (`transform`, kbId: `javax.email-send-to-azure-communication-service-email`) — Migrate JavaMail to Azure Communication Service Email.
- **010-transform-migrate-to-console-logging** (`transform`, kbId: `log-to-console`) — Migrate file-system logging to console logging.
- **011-transform-migrate-aws-region-to-azure-region** (`transform`) — Migrate AWS region configuration to Azure region configuration.
- **012-transform-migrate-local-resource-to-azure** (`transform`) — Migrate localhost / local JDBC resources to Azure managed services.

### Phase 3 — Credential Hardening
- **013-transform-plaintext-credential-to-azure-keyvault** (`transform`, kbId: `plaintext-credential-to-azure-keyvault`) — Move plaintext credentials to Azure Key Vault.
- **014-transform-remove-hardcoded-credentials** (`transform`) — Remove hard-coded and default/well-known credentials.

### Phase 4 — Dependency CVE Remediation
- **015-security-scan-and-fix-cve** (`security`, skill: `validate-cves-and-fix`) — Scan and fix known high-severity CVEs in declared dependencies.

---

## Notes

- Detailed, programmatic task definitions (requirements, dependencies, success criteria) are tracked in [.metadata/tasks.json](.metadata/tasks.json).
- Tasks are chained sequentially because several transforms touch the same configuration files (notably the asset-manager web/worker `application.properties`), avoiding concurrent edit conflicts.
- The "Secure with Managed Identity" tasks (Azure SQL, PostgreSQL, MySQL) reduce stored passwords; the credential-hardening phase then removes any remaining plaintext/hard-coded secrets.
- A CVE remediation task is always included per modernization guidelines; the assessment was run with a minimum CVE severity of high.

---

## Open Questions & Questionnaire

- [x] Q: What Java target version should the upgrade use? → A: Latest LTS (Java 25), JDK-only upgrade (no framework upgrade unless required for compatibility).
- [x] Q: What authentication method should be used for migrated Azure data services? → A: Managed Identity (default) where applicable.

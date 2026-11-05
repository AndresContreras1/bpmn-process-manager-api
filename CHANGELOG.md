# Changelog

The notable changes of each version. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the versions will follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html) from the first one that is
tagged. Each pull request adds its line under *Unreleased*.

## [Unreleased]

### Added

- Supply-chain checks: every action pinned to a commit SHA, a read-only token per job, Dependabot, CodeQL, gitleaks,
  a CycloneDX SBOM of the API and of the web app, and Trivy over both images.
- Mutation testing with PIT every night, and a format check on the files each change touches.
- A request id in every response, log line and error; Prometheus metrics on a management port of their own, with
  business counters; JSON logs in `prod`.
- Tracing with OpenTelemetry: a `traceId` in the logs and the errors, and spans of the engine and the diagnosis.
- Repository governance: architecture decision records, a runbook, a security policy and `security.txt`, pull
  request and issue templates, code owners and this changelog.
- Several instances of the API can run on one database: closed sessions are announced to every instance with
  PostgreSQL's `LISTEN/NOTIFY`, failed logins and AI reviews live in tables, and each scheduled job runs in one
  instance at a time with ShedLock.

### Changed

- Development and tests run on PostgreSQL 16, like production: `dev` brings up `compose.dev.yaml` through Spring
  Boot's Docker Compose support, every test runs against one Testcontainers server with a database per context,
  and H2 is out of the build. The migrations are a single set.
- Java 25, with virtual threads and a graceful shutdown.
- A minimal image of the API with the JVM's AOT cache, and read-only containers without Linux capabilities.
- The server's error page answers in Problem Details, like the rest of the API.

### Security

- Spring Boot 4.1.1, Tomcat 11.0.26 and Jackson 3.1.7 and 2.21.7 for the vulnerabilities Trivy found in the API image,
  and NGINX 1.30 for the ones in the web image.

## Before this file

The history up to here is in the
[merged pull requests](https://github.com/AndresContreras1/bpmn-process-manager-api/pulls?q=is%3Apr+is%3Amerged).

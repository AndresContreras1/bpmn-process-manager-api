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
- Spring Modulith verifies the modules beside ArchUnit and documents them in `docs/modules`. Events between modules
  go through an outbox in PostgreSQL and are delivered again after a restart, and slow work goes to a queue of
  jobs taken with `FOR UPDATE SKIP LOCKED`, with retries that wait longer each time, failed jobs kept for a week
  and a cap of jobs running per store.
- Transactional e-mail through any SMTP server, sent from the queue of jobs, with templates in Spanish, English
  and French: verifying the e-mail, recovering the password and inviting by e-mail (HU-02.1), each with a link
  that works once, expires, and is kept only as its SHA-256. `dev` catches the e-mails in Mailpit.
- Session limits each store chooses, within what NIST SP 800-63B-4 asks for AAL2: from 30 to 60 minutes without
  being renewed and from 1 to 24 hours after the login, an hour and a day by default.
- Closing a store: 30 days of read-only grace in which it can be cancelled, and then the nightly purge deletes
  every row of the store and leaves of it only the id and the dates. Anonymizing a person on request: their user
  stays under a pseudonym, and the history names them by it.

### Changed

- **Breaking:** a password chosen by a person needs 15 characters and passes the policy of NIST SP 800-63B-4, and
  the demo administrator signs in with `every-order-on-time`. `JWT_REFRESH_EXPIRATION_SECONDS` is gone: the
  refresh token lives what each store decides.
- **Breaking:** the login, the renewal and the password change no longer return the tokens in the body, and the API
  no longer reads `Authorization: Bearer`; the refresh and the logout take the refresh token from its cookie. The
  web app, the end-to-end tests, k6 and the Postman collection use the cookies.
- Development and tests run on PostgreSQL 16, like production: `dev` brings up `compose.dev.yaml` through Spring
  Boot's Docker Compose support, every test runs against one Testcontainers server with a database per context,
  and H2 is out of the build. The migrations are a single set.
- Java 25, with virtual threads and a graceful shutdown.
- A minimal image of the API with the JVM's AOT cache, and read-only containers without Linux capabilities.
- The server's error page answers in Problem Details, like the rest of the API.

### Security

- The browser's session lives in two `HttpOnly`, `Secure`, `SameSite=Lax` cookies, and what changes something with
  them, the login included, needs the CSRF token of the `XSRF-TOKEN` cookie in `X-XSRF-TOKEN`. The access token
  follows RFC 8725 (`iss`, `aud`, `jti`, `kid`), and the signing key can rotate without closing sessions.
- Security headers on every response. The API forbids loading anything and being framed; the web app allows only
  its own origin, and for scripts only the hashes Angular computes when it builds. HSTS, `nosniff`,
  `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, COOP and CORP on both, and Trusted Types in report
  mode. In `prod` the API believes the proxy's `X-Forwarded-*` only from the internal network, and the Compose
  stack publishes the API's own port only on `127.0.0.1`. The leftovers of the H2 console are gone.
- Passwords follow NIST SP 800-63B-4: at least 15 characters while they are the only factor and no composition
  rules, checked against a list of leaked and common ones and against the name, e-mail and store of whoever chooses
  them, up to the 72 bytes BCrypt reads. Every hash carries its algorithm, and a login redoes an old one.
- Spring Boot 4.1.1, Tomcat 11.0.26 and Jackson 3.1.7 and 2.21.7 for the vulnerabilities Trivy found in the API image,
  and NGINX 1.30 for the ones in the web image.

## Before this file

The history up to here is in the
[merged pull requests](https://github.com/AndresContreras1/bpmn-process-manager-api/pulls?q=is%3Apr+is%3Amerged).

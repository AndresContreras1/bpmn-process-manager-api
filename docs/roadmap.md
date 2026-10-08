[← Documentation](README.md)

# Roadmap

**Peak-traffic readiness**
- [x] k6 load tests that simulate a sales peak and an order peak, with thresholds in CI
- [x] Connection-pool sizing by environment variable, with every request on a virtual thread
- [x] In-memory cache of what a published version says, keyed by store and version, with no invalidation to get wrong
- [x] `Pageable`-based pagination with stable sorting for every collection that can grow

**Consistency under concurrency**
- [x] Optimistic locking with `@Version`, so two editors cannot overwrite each other (`409 Conflict`)
- [x] Idempotency keys on create requests, so a retried call does not duplicate a process
- [x] Process versioning: publishing freezes the diagram as a version and what is edited afterwards is the draft

**Security**
- [x] Globally unique user emails, so a new store cannot reuse an existing user's login email
- [x] Read-only process sharing between stores (HU-23), through a read door that no change can use
- [x] Authentication through `AuthenticationManager` and `UserDetailsService`, without revealing whether an email exists
- [x] Short-lived access tokens with refresh tokens
- [x] Rate limiting on login (`429` with `Retry-After`)
- [x] Scheduled purge of expired sessions, refresh tokens and old idempotency keys

**Data and auditability**
- [x] Flyway migrations with `ddl-auto=validate`, composite unique constraints and `empresa_id` indexes
- [x] Soft delete and change history for every BPMN element
- [x] Auditing fields (`createdBy`, `lastModifiedBy`) filled from the authenticated principal
- [x] Lazy associations with entity graphs and read-only transactions

**API contract**
- [x] Full OpenAPI documentation (`@Tag`, `@Operation`, `@ApiResponse`, `@Schema`), not published in production
- [x] Field-level validation errors in Problem Details
- [x] Aggregate endpoint that returns a complete BPMN diagram for the back-office web app

**Running processes**
- [x] Cases that run a published version: tokens as steps, the tray of tasks by process role, and the timeline
- [x] A condition language of its own, checked when publishing and evaluated when running, that cannot execute code
- [x] A row lock per case, so two people completing the same task do not complete it twice
- [x] Memberships, so each person can ask for the tray of their own process roles
- [x] Messaging and a simulation clock: sending, correlating and waiting for the messages the diagram declares
- [x] Simulated partners for payments, shipping and notifications, deterministic by store and seed
- [x] An operations dashboard: cases by state, cycle time and open tasks per role

**Beyond the model**
- [x] Deterministic diagnosis of a diagram, with its own catalogue of errors and warnings and a what-if for a deletion
- [x] AI review of a diagram, with the answer validated against a schema and limited per store
- [ ] Review of a change instead of the whole diagram, so the model only reads what moved

**Architecture and quality**
- [x] Request and response DTO packages with MapStruct mappers, and services exposed as interfaces
- [x] Module boundaries between `gestion` and `modelado` enforced by ArchUnit, with no dependency cycles
- [x] Complete Spring profiles: `dev` with seed data, `test` with an isolated database per context, and `prod`
- [x] Repository tests with `@DataJpaTest` and unit tests for every modeling service
- [x] Coverage gate per package, branches included
- [x] Mutation testing with PIT over the services, the engine and the conditions, every night, with a floor
- [x] Spotless over the files each change touches: imports in order and without unused ones, no trailing spaces
- [x] A SonarCloud quality gate on top of it, which fails the build as soon as the project token is in the repository secrets
- [x] Docker Compose with PostgreSQL and Actuator health checks
- [x] PostgreSQL everywhere: `dev` through Docker Compose and every test through Testcontainers, with H2 out of
      the build

**Runtime**
- [x] Java 25, with virtual threads and a graceful shutdown that lets the requests in progress finish
- [x] An image of the API on a minimal JRE with the JVM's AOT cache, trained at build time
- [x] Read-only, unprivileged containers without Linux capabilities

**Observability**
- [x] Actuator on a management port of its own in `prod`, with the metrics in the Prometheus format
- [x] Business counters: what happens to the cases, the versions published and the failed logins, counted on commit
- [x] JSON logs in the Elastic Common Schema in `prod`
- [x] A request id in the `X-Request-Id` header, in every log line and in every Problem Details, errors outside the
      controllers included
- [x] Tracing with OpenTelemetry: a `traceId` in the logs and the errors, spans of the engine and the diagnosis, and
      OTLP export with a configurable sampling rate

**Supply chain**
- [x] Every GitHub Action pinned to a commit SHA, with a CI step that fails otherwise, and a read-only token per job
- [x] Dependabot for Maven, npm, Actions, Docker and Compose, with a seven-day cooldown, and base images pinned by digest
- [x] CodeQL over Java, TypeScript and the workflows, and gitleaks over every commit
- [x] Trivy over both images: a fixable critical vulnerability fails the pipeline
- [x] CycloneDX SBOM of the API, inside the jar, and of the web app

**Repository governance**
- [x] A security policy with a private way to report, and `security.txt` served by the web app
- [x] Pull request and issue templates, code owners and a changelog
- [x] A runbook: health, following a failed request, what to watch, incidents and backups
- [x] Architecture decision records for the decisions of plan v3, each with what checks it

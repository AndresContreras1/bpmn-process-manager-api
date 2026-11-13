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
- [x] Verifying the e-mail, recovering the password and inviting by e-mail, with single-use links
- [x] Passwords as NIST SP 800-63B-4 asks: 15 characters and no composition rules, checked against leaked ones and
      the person's own data, with hashes that carry their algorithm and are redone on login
- [x] Sessions with an idle timeout and a maximum duration that each store chooses, within AAL2
- [x] A second factor with an authenticator app and recovery codes, a login in two steps, a recent verification
      for what changes it, and a policy per store that asks for it from administrators or from everybody
- [x] Passkeys (WebAuthn), as a second factor and as a sign-in without a password
- [x] Signing in with Google or Microsoft (OpenID Connect with PKCE), linking an account only after its owner
      confirms it, verified e-mail domains that join a store, and a store that signs in only with a provider
- [x] Closing a store with 30 days of read-only grace and then deleting everything it had, and anonymizing a
      person on request
- [ ] Exporting a store's data during the 30 days of its closing
- [x] The browser's session in `HttpOnly` cookies with a CSRF token, and a JWT as RFC 8725 asks, with a signing key
      that rotates without closing sessions
- [x] Rate limiting on login (`429` with `Retry-After`)
- [x] Rate limiting on every change, in token buckets every instance shares through PostgreSQL, a cap on the body
      of a request, and Cloudflare Turnstile on the registration of a store
- [x] Scheduled purge of expired sessions, refresh tokens and old idempotency keys
- [x] Several instances on one database: closed sessions announced with `LISTEN/NOTIFY`, failed logins and AI
      reviews in tables, and every scheduled job locked with ShedLock
- [x] Security headers on the API and the web app: a strict CSP with the script hashes of the build, HSTS, COOP
      and CORP, and Trusted Types in report mode; the forwarded headers believed only from the internal network
- [ ] HSTS preload, a month after production runs on HTTPS

**Authorization**
- [x] Grants on folders and processes, to a person, a process role or the whole store, inherited down the folders,
      decided by one evaluator in three layers: the access role, the grants and separation of duties
- [x] Lists that only show what each person can see, filtered in the same query
- [ ] A screen to grant and revoke, and the web hiding what a person cannot do

**Data and auditability**
- [x] Row-level security in PostgreSQL on every table of a store, with a role that cannot bypass it
- [x] An audit trail per store chained with SHA-256, that nobody can change or delete, fed from the outbox, with a
      verification that names where it breaks and a CSV export safe from formulas
- [x] The daily anchor of the last hash in a bucket with Object Lock, checked when the trail is verified
- [x] Flyway migrations with `ddl-auto=validate`, composite unique constraints and `empresa_id` indexes
- [x] Soft delete and change history for every BPMN element
- [x] Auditing fields (`createdBy`, `lastModifiedBy`) filled from the authenticated principal
- [x] Lazy associations with entity graphs and read-only transactions
- [x] Statement and idle-in-transaction timeouts for the role of the API, connection leak warnings, batched
      updates, an index behind every foreign key and `pg_stat_statements`

**API contract**
- [x] Full OpenAPI documentation (`@Tag`, `@Operation`, `@ApiResponse`, `@Schema`), not published in production
- [x] Field-level validation errors in Problem Details
- [x] Aggregate endpoint that returns a complete BPMN diagram for the back-office web app

**BPMN**
- [x] XML read with hardened parsers and limits, and the geometry of every element (BPMN DI) kept in the model
- [x] BPMN 2.0.2 export of a process and of each version, valid against the OMG schemas, with what BPMN has no
      element for in an extension of its own
- [x] Import from any tool by differences, with a report, the hard rules of the model and what other tools wrote kept
      for the way back; a corpus of real files in the build and a fuzzer every night
- [x] Documental processes, timers, boundary events, terminate ends, tasks without a type, annotations, groups and
      data objects
- [x] Sub-processes and call activities, with circular calls refused when publishing
- [x] A modeler on bpmn-js with the rules of the model, a properties panel, the diagnosis as markers, autosave and a
      local copy until the server confirms
- [x] One drawing everywhere: the viewers of processes, versions and cases on the same component, the case as
      markers and counters
- [x] A structure view that does everything the canvas does from the keyboard, and targets of at least 24 × 24 px

**Editing with others**
- [x] `ETag` and `If-Match` on every editable resource, autosave of the whole model, and draft revisions
- [x] A comparison by meaning between versions, the draft or a file, matched by `bpmnId`
- [x] Presence over server-sent events shared by every replica, a soft lock and *take control*, in the API and the web

**Process repository**
- [x] A tree of folders, and for each process its owner, next review, classification and tags, with their filters
- [x] Documents attached to a process or an element, with versions, a type from the content, an antivirus and ZIP
      bomb check in the queue, a quota per store and downloads by signed link, on S3 or compatible storage
- [x] Documentation in Markdown per process and element, cleaned on the server, and a characterization sheet
- [x] The process manual in HTML and PDF, built in the queue from the published version and the drawing the web
      app sends when publishing
- [x] Comment threads on a process, an element or a version, with mentions of who can view it, resolve and reopen,
      and a notification center in the app, over server-sent events and by e-mail, immediate or in a daily digest
- [ ] Review and approval, and full-text search

**Running processes**
- [x] Cases that run a published version: tokens as steps, the tray of tasks by process role, and the timeline
- [x] A condition language of its own, checked when publishing and evaluated when running, that cannot execute code
- [x] A row lock per case, so two people completing the same task do not complete it twice
- [x] Memberships, so each person can ask for the tray of their own process roles
- [x] Messaging and a simulation clock: sending, correlating and waiting for the messages the diagram declares
- [x] Simulated partners for payments, shipping and notifications, deterministic by store and seed
- [x] An operations dashboard: cases by state, cycle time and open tasks per role
- [x] Timers that run (waits, boundary timers and timer starts) on the wall clock and a business calendar per
      organization with the public holidays of Colombia, the United States, France and Australia; test cases keep the
      ticks, and a draft can be tried without publishing it (PR 68)
- [x] Typed forms on user tasks and start events, frozen in the version and carried in BPMN: twelve field types,
      RE2 patterns, conditions the server compiles for the web, and every answer checked by the server (PR 69)
- [ ] The form designer in the modeler and the form a person fills in the web (F23)
- [x] Assignment of user tasks (role tray, fixed person, initiator or the person of a form field), frozen in the
      version; claim, release, reassign and delegate with a reason; inboxes and the workload per person; only the
      holder completes a task, and an administrator on someone's behalf only by store policy (PR 70)
- [ ] The inboxes, the workload, reassigning and delegating in the web's work portal (F24), and the assignment panel
      in the modeler beside the form designer (F23)
- [x] Deadlines on user tasks and on whole cases, in hours or business days; on time, due soon and overdue, with a
      notice once at each change to the holder (or the role) and the process owner; escalation from a non-interrupting
      boundary timer that notifies or hands the task to a supervisor; compliance per process, activity and person in
      the dashboard; inboxes by due date (PR 71)
- [ ] Deadlines and escalation in the modeler's panel, and due dates in the work portal (F23, F24)

**Beyond the model**
- [x] Deterministic diagnosis of a diagram, with its own catalogue of errors and warnings and a what-if for a deletion
- [x] AI review of a diagram, with the answer validated against a schema and limited per store
- [ ] Review of a change instead of the whole diagram, so the model only reads what moved

**Architecture and quality**
- [x] Request and response DTO packages with MapStruct mappers, and services exposed as interfaces
- [x] Module boundaries between `gestion` and `modelado` enforced by ArchUnit, with no dependency cycles
- [x] Spring Modulith verifying the declared modules beside ArchUnit, and their documentation generated in
      `docs/modules`
- [x] An outbox in PostgreSQL for the events between modules, and a queue of jobs with retries, failed jobs and a
      cap per store
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

**Backup and recovery**
- [x] Continuous WAL archiving, a full backup every week and a differential every day with pgBackRest, encrypted, into
      a bucket with Object Lock, and the backups' bucket in another account in production (PR 67)
- [x] Documents kept with every version for 35 days, and their replication to another account described for AWS
- [x] A restore drill every month, in a workflow: an empty container recovers the last backup, and the rows of every
      table, every audit trail and every version fingerprint are checked, with the RPO and the RTO measured

**Supply chain**
- [x] Every GitHub Action pinned to a commit SHA, with a CI step that fails otherwise, and a read-only token per job
- [x] Dependabot for Maven, npm, Actions, Docker and Compose, with a seven-day cooldown, and base images pinned by digest
- [x] CodeQL over Java, TypeScript and the workflows, and gitleaks over every commit
- [x] Trivy over the three images: a fixable critical vulnerability fails the pipeline
- [x] CycloneDX SBOM of the API, inside the jar, and of the web app

**Repository governance**
- [x] A security policy with a private way to report, and `security.txt` served by the web app
- [x] Pull request and issue templates, code owners and a changelog
- [x] A runbook: health, following a failed request, what to watch, incidents and backups
- [x] Architecture decision records for the decisions of plan v3, each with what checks it

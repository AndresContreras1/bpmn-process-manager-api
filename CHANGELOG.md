# Changelog

The notable changes of each version. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the versions will follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html) from the first one that is
tagged. Each pull request adds its line under *Unreleased*.

## [Unreleased]

### Added

- Assignment of user tasks: to the tray of the lane's role (the default), to a fixed person, to whoever opened the case
  or to the person a case variable names, such as a `PERSONA` field of an earlier form. `PUT
  /actividades/{id}/asignacion` saves the rule; it is frozen in the version, travels in BPMN as `bpm:asignacion` and
  the diagnosis checks it as `E-23` and `A-17`. When the rule cannot tell an active person, the task waits in the role
  tray and the case timeline says why.
- Claiming, releasing, reassigning and delegating a task with a reason: `POST /tareas/{id}/reclamar`, `/soltar`,
  `/reasignar` and `/delegar`, each one in the case timeline and the audit trail. A delegated task goes back to whoever
  delegated it when the delegate releases it, if that person can still hold tasks, and only the delegator or an
  administrator reassigns it. A delegation ends as `ANULADA` when the case cancels, fails or interrupts its task. Every
  action locks the case first; two people claiming the same task at once: one gets it, the other a `409`.
- Inboxes: `GET /tareas?bandeja=MIAS|DE_MIS_ROLES|SIN_ASIGNAR|TODAS`, oldest first, and the workload per person,
  `GET /tareas/carga`.
- The store policy `administradorCompletaPorOtro`, off by default: an administrator completes a task another person
  holds, recorded in the audit trail as `TAREA_COMPLETADA_POR_OTRO`.
- Typed forms on user tasks and plain start events: text, long text, number, money, date, date and time, yes/no,
  list, multiple list, person, file and section, with required, minimum and maximum, length and a pattern evaluated
  with RE2/J in linear time (nested counted repetitions are capped before compiling). Fields show and become required
  by conditions in the gateway grammar, which the server compiles into a JSON tree for the web to walk, and take a
  default from a case variable. `PUT /actividades/{id}/formulario` and `PUT /eventos/{id}/formulario` save a form;
  `GET /tareas/{id}/formulario` and `GET /procesos/{id}/formulario-de-inicio` read it. The form is frozen in the
  version, travels in BPMN as `bpm:formulario` and comes back on import, and the diagnosis checks it as `E-22`.
  Completing a task or opening a case validates the answers against the version's form (`400` with `errors` per
  field) and stores them with their types under `tarea.<name>`; the tasks screen shows each rejected key next to the
  message.
- A business calendar per organization: country (Colombia, United States, France or Australia), time zone, working
  hours and working days, with each country's public holidays computed (Ley 51 de 1983 and Easter for Colombia, the
  federal ones with their observed day, the French jours feries, the Australian national ones with their additional
  day) and editable day by day by an administrator, audited. Business time is counted with it, across weekends,
  holidays and clock changes.
- Timers that run: a wait, a boundary timer that interrupts its activity or only warns (a cycle warns every time), and
  a timer start that opens cases on its own, with ISO 8601 durations, dates and cycles, cron cycles, and a new business
  duration (`DURACION_HABIL`, `bpm:habil` in BPMN). Production timers fire from a scheduler, once, also with two
  instances or after the API was down; test timers fire with the store's tick. A timer whose case ends, or whose
  activity stops waiting, is cancelled. `GET /casos/{id}/temporizadores` lists them, and `GET
  /procesos/{id}/temporizadores` pages the timer starts. Timer expressions have caps the diagnosis checks: a hundred
  years for a duration, 366 working days for a business one, dates from 1900 to 9999 and a cycle at most once a
  minute.
- Two modes for a case: `PRODUCCION` on the wall clock and the business calendar, and `PRUEBA`, the default, on the
  ticks and simulated partners as before. The simulated partners only answer test cases.
- Trying a draft without publishing it: `POST /procesos/{id}/pruebas` runs it as a test case on a throwaway test
  version that no list shows and the nightly purge deletes after `PRUEBAS_RETENCION`. Incoming messages never
  correlate with a test run, and the dashboard and the open-cases gauge leave test runs out.
- Backups with pgBackRest: PostgreSQL archives every WAL segment within a minute, and a `respaldo` service takes a
  full backup every Sunday and a differential every other day, into a bucket with Object Lock in compliance mode, every
  file encrypted with `RESPALDO_CLAVE`. After each backup the rows of every table are counted and a restore point is
  left at that instant. The database runs as `postgres`, read-only and without capabilities, from an image of its own
  that Trivy scans.
- The restore drill, `ops/respaldo/simulacro.sh`, and a monthly workflow that runs it: the last backup is restored
  into an empty container, its rows are compared with the count, and the API, started with `RESPALDO_VERIFICAR=true`,
  verifies every audit trail against its anchor and every published version against its fingerprint. It prints the
  RPO and the RTO it measured.
- The documents' bucket keeps every version for 35 days (`ALMACEN_S3_DIAS_DE_VERSIONES`) when the API prepares it.
- Comments and notifications: threads on a process, one of its elements or a published version, in Markdown cleaned
  like the documentation, with replies, resolve and reopen. Every role comments; mentions are limited to people who
  can view the process (400 names the others, with the same reason for all). Mentions, replies and resolved threads
  reach a notification center that only its recipient sees, a server-sent event stream shared by every replica, and
  e-mail in the person's language, immediate or in a daily digest as each person chooses per type. Resolving,
  reopening and deleting are audited; read notifications are purged after 90 days.
- Documentation in Markdown for a process and each of its elements, converted on the server and cleaned against a
  whitelist, with a preview; a characterization sheet per process; the drawing of each version, which the modeler
  sends right after publishing and the server cleans; and the manual of a published version in HTML and PDF, built in
  the job queue in the language of whoever asks it and downloaded through a signed link.
- Documents attached to a process or one of its elements, with versions: the type comes from the content against a
  whitelist (415), the name is cleaned, uploads stream up to 25 MB within a quota per store (413), and the job queue
  reviews each version with ClamAV and, for Office and OpenOffice files, a ZIP bomb check before it can be downloaded.
  Downloads are five-minute signed links served by the API as attachments. The storage is S3 or compatible, with
  SeaweedFS in `compose.yaml`, or a disk folder in development.
- The daily anchor of the audit trail: each store's last link goes to a bucket with Object Lock in compliance mode,
  and verifying the trail checks the latest anchor.
- Granular permissions: actions granted on folders and processes to a person, a process role or the whole organization,
  inherited from the folder, on top of the access role and with separation of duties. Lists only show what can be seen.
- The process repository: a tree of folders, and for each process its folder, owner, next review date, classification
  and tags, with new filters and sort orders in the list. `GET /roles/{id}` lists the processes that use the role.
- Presence and comparison in the web: who is on a process, who is editing it and how to take control; a conflict can be
  compared before reloading; `/procesos/{id}/comparar` shows two states of a process with colors and a text legend.
  The live connection reconnects with a growing wait.
- A structure view in the modeler: the diagram as a keyboard-navigable tree (pools, lanes, nodes and their outgoing
  flows) that can create, connect, move between lanes, rename and delete without dragging, with live announcements.
  Targets of at least 24 × 24 px in the canvas and a visible, unobscured focus.

- The BPMN modeler in the web, on bpmn-js: `/procesos/{id}/modelar` draws and edits the process, limited to what the
  model has and to its mode, with undo, search, minimap, import and export to `.bpmn`, SVG and PNG, a properties panel,
  autosave with a visible status and a local copy, and the diagnosis as markers.
- Presence and soft lock: `GET /api/v1/procesos/{id}/en-vivo` streams who is looking, who is editing, saves, publications
  and control being taken, across replicas with PostgreSQL `LISTEN/NOTIFY`. `/bloqueo` starts, keeps, releases and takes
  over editing, which expires without a heartbeat; taking over is audited.
- Semantic diff: `GET /api/v1/procesos/{id}/comparar?de=&a=` says what was added, removed, changed and only moved
  between two versions or the draft, by `bpmnId`; `POST` the same path with a file says what saving it would do.
- Autosave and draft revisions: editable resources carry `ETag` and accept `If-Match` (`412` when stale). `PUT
  /api/v1/procesos/{id}/modelo` saves one at a time, compares `If-Match`, accepts an `Idempotency-Key`, and writes
  nothing if the file changes nothing. `GET, POST /api/v1/procesos/{id}/revisiones` and `POST .../{revisionId}/restaurar`
  keep and restore save points of the draft.
- Sub-processes and call activities: an expanded sub-process contains its own flow (`PUT
  /api/v1/nodos/{id}/contenedor`), flows do not cross its border, and it is drawn as the box of its content; a call
  activity points to another process of the store (`PUT /api/v1/actividades/{id}/llamada`). Exported and imported.
  Circular calls are rejected when publishing; new diagnosis codes `E-19`, `E-20`, `E-21` and `A-16`.
- Descriptive BPMN: a process can be `DOCUMENTAL` (`PUT /api/v1/procesos/{id}/modo`): it is published and documented,
  opens no cases, and its diagnosis only checks the structure. Text annotations, groups, data objects and data stores
  (`/api/v1/procesos/{id}/artefactos`), associations and data associations (`/api/v1/procesos/{id}/asociaciones`),
  tasks without a kind, timer start, catch and boundary events and the terminate end, which closes every path of the
  case. All of them are exported and imported. New diagnosis codes: `E-16`, `E-17`, `E-18` and `A-15`.
- BPMN import: `POST /api/v1/procesos/importar` turns a file from this product, Camunda Modeler, bpmn.io or any other
  tool into a draft process, and `PUT /api/v1/procesos/{id}/modelo` applies one to a draft by differences, by
  `bpmnId`. Each element is imported, converted to the closest thing or left out, and the report says which and why;
  hard rules answer `409` with every problem. What other tools write in an element (Camunda's attributes and
  properties, bpmn.io's colors) is kept and exported again. A golden corpus with the OMG MIWG class A models and
  bpmn-js exports goes there and back with no change, and a nightly fuzzer breaks it on purpose.
- BPMN 2.0.2 export: `GET /api/v1/procesos/{id}/modelo` downloads a process as a `.bpmn` file that validates against
  the OMG schemas and opens in bpmn.io and Camunda Modeler, with its geometry; what BPMN has no element for goes in
  `extensionElements` under its own namespace, with its own schema. Each published version stores its file, and
  `GET .../versiones/{numero}/modelo` answers it; a Java migration writes it for the versions that already existed.
  The diagram's elements now carry their `bpmnId`.
- The diagram's geometry in the domain, ready for BPMN DI: a stable `bpmn_id` for every pool, lane, node and flow,
  and the absolute bounds, waypoints and label bounds of each one, recalculated with every change to a process with
  the same calculation as the web. A Java migration converts the diagrams that already existed.
- `SecureXml`, the only way to create an XML parser: no DTDs, no external entities, limits on size, elements,
  depth, attributes, text and time, and a 400 for whatever breaks them. An architecture rule forbids creating XML
  factories anywhere else.

- The account's security in the web: the screens of the e-mail links (verify, recover the password, accept an
  invitation) and of linking Google or Microsoft; the second factor with an authenticator app from a QR code, recovery
  codes and passkeys; signing in with a passkey or a provider and finishing a half-done login; a dialog for the recent
  verification the API asks for; the open sessions, which `GET` and `DELETE /api/v1/auth/sesiones` now list and close;
  and, for administrators, the second-factor policy, e-mail domains, closing the organization, inviting by e-mail,
  anonymizing a person and the audit log with filters, export and the chain check. The profile says when the e-mail
  was verified.

- The web no longer keeps who signed in in the browser: it asks `GET /api/v1/auth/sesion` when it starts. Tabs renew
  the session one at a time and tell each other when someone signs in, renews or signs out, so signing out in one
  signs out all of them; and a notice two minutes before the session closes for being idle offers to keep it open.
  Every session answer says how long the session survives without being renewed and how long it has left.

- The web in Spanish of Colombia, English of the United States and of Australia, and French: one build per language,
  with its dates, numbers and currency, that NGINX chooses by the cookie of the language menu or by the browser's
  `Accept-Language`. Each organization and each person keep a language and an IANA time zone
  (`/api/v1/auth/preferencias`, `/api/v1/empresas/actual/preferencias`); an organization starts in the language of
  whoever registers it, the e-mails go in the language of whoever receives them, and the dates are shown in the time
  zone of whoever reads them. The web says *organization* where it said *store*.

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
- A second factor with an authenticator app (TOTP, RFC 6238) and ten single-use recovery codes. With it the
  password leaves a half-done login that only works to send the code. What changes the second factor, and closing
  the store, asks for a verification of the last ten minutes, and each store can ask for a second factor from its
  administrators or from everybody.
- Signing in with Google or Microsoft (OpenID Connect with PKCE), into the same session cookies as the login. An
  account of a provider is linked to an existing user only after the owner confirms it with the password or the
  second factor; a store can verify its e-mail domains with a TXT record so that its people join with a default
  role, and can ask its people to sign in only with a provider.
- A rate limit on every change, with token buckets that every instance shares through PostgreSQL: signing in,
  registering a store and what sends e-mail count by address, every other change counts by person, and an empty
  bucket answers `429` with `Retry-After`. Bodies over 1 MB answer `413`, and Cloudflare Turnstile can guard the
  registration of a store.
- An audit trail per store that shows when it is touched: each entry carries the SHA-256 of its fields and of the
  previous one, a trigger turns down any change or deletion, and the administrator reads it, exports it to CSV or
  JSON and verifies the chain at `/api/v1/auditoria`.
- Row-level security in PostgreSQL on every table of a store: the API works as a role that owns nothing and cannot
  bypass it, and each connection carries the store of whoever asks, so a query that forgets to filter cannot see or
  write another store's rows.
- Passkeys (WebAuthn): as the second factor after the password, to confirm who someone is, and to sign in
  without a password. Each ceremony keeps its challenge in PostgreSQL, works once and asks the device to verify the
  person; passkeys live with the store of their owner, under row-level security.
- Database health: each connection of the API gets a statement timeout and an idle-in-transaction timeout when it
  takes its role, the pool warns of a connection that does not come back, Hibernate sends repeated updates in
  batches, every foreign key has an index, and `pg_stat_statements` counts what each query costs.

### Changed

- Only whoever holds a task completes it. A task nobody holds is taken by completing it, by a member of its process role
  or an administrator; anybody else gets `403`, and a task another person holds answers `409`. `POST
  /tareas/{id}/asignar` is replaced by `reclamar` and `soltar`, which the tasks screen now calls; taking a task by
  completing it is recorded as a claim. The tasks screen offers "Complete" only on a task the viewer holds or nobody
  holds, and the case timeline names the assignment events.
- Free key-value data, when completing a task or opening a case without a form, is taken only in `PRUEBA` mode; a
  `PRODUCCION` case answers `400` for each key, and its variables are corrected with `PATCH /casos/{id}/variables`.
- `E-18` no longer stops an executable process from publishing a timer: it now flags a cycle on a timer that fires only
  once. A case step keeps a subtype of up to 30 characters, so `TEMPORIZADOR_INTERMEDIO` fits.
- The README and the guides cover BPMN files, the bpmn-js modeler, editing together, the process repository and
  permissions, with the numbers of today's build.
- The process, version and case viewers draw on bpmn-js, with the case's progress as markers and token counters. The
  old SVG canvas and its element-by-element editor are gone; `/procesos/{id}/editar-diagrama` redirects to the modeler,
  which can publish.
- Unit tests for the services that only the integration suites checked (second factor, external sign-in, sessions,
  accounts, settings, the audit log, permissions, folders, BPMN import, autosave and live editing among them): the
  nightly mutation run is back above its 75 % floor, with 2984 of 3274 mutations caught (91 %).
- The web app has a design system: tokens in the DTCG format turned into Bootstrap's Sass variables and CSS
  variables, a green scale checked against WCAG AA, a dark mode, a shell with a sidebar, breadcrumbs and a search,
  shared components for loading, empty, errors, notices and statuses, and axe-core over every screen in the
  end-to-end suite.
- The web app runs on Angular 22, built with `@angular/build` on Node 24, linted with angular-eslint, formatted with
  Prettier and with Vitest for its logic; only three plugins of Bootstrap's JavaScript are loaded.

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
- The error handler writes the request path, the database's message and an external service's answer to the log
  on one line: a line break inside them turns into an underscore and can no longer fake a log line of its own.
- The CSRF token lasts the session: signing in, by any way, and signing out set a new one, and no other request
  changes it. Before, every request with the session cookies answered a new token, and a change sent alongside other
  requests could get a `403`; the web now stays in its language, with a notice, when saving it fails.

## Before this file

The history up to here is in the
[merged pull requests](https://github.com/AndresContreras1/bpmn-process-manager-api/pulls?q=is%3Apr+is%3Amerged).

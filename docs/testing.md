[← Documentation](README.md)

# Quality and testing

```bash
./mvnw verify
```

The build runs 1216 tests and a JaCoCo coverage gate. The HTML report is written to `target/site/jacoco/index.html`.

| Suite | Tests | Scope |
|---|---:|---|
| Architecture (ArchUnit) | 40 | Layering, module boundaries and cycles between packages at both levels, DTOs and mappers, tenant isolation — in the database and in memory, where every cache key has to name the store — JPA mapping (inheritance, its own soft delete per subtype, enums, lazy associations), no `HttpSession`, a simulated partner that cannot reach into the engine or open a connection, a port that cannot mention an entity, anything that runs on its own living in `config`, and a declared profile in every `@SpringBootTest` and persistence slice |
| Controller slices (`@WebMvcTest`) | 192 | Routes, status codes, JSON shape and validation, with the real security rules |
| Service unit tests (Mockito) | 429 | Business rules of the three modules, with the repositories mocked: what each service accepts, what it refuses and what it drags along; the diagnosis catalogue, with a test that fires each code over a diagram that is right everywhere else and one that proves the healthy diagram fires none; the language of the conditions, compiled and evaluated, operator by operator; the graph a published version turns into; the engine, with one test per row of the table of what each node does, on diagrams built in memory, messages included: what a node sends, what it waits for and what each `siFalla` does when a send does not arrive; the fingerprint of a diagram, which has to change with any change of any element and stay put with everything else; the AI review against a stubbed HTTP server: what it asks for, what it accepts as an answer and what it refuses; the four simulated partners, one suite each, with the seed proving that the same store and the same steps always decide the same way; and the cycle time over lists counted by hand, where out of twenty orders one slow one does not move the p95 and two do |
| Repository slices (`@DataJpaTest`) | 70 | The hand-written queries against the real Flyway schema: the read gate for shared processes, the search filters — of processes and of users, where a deactivated colleague only turns up when asked for — the ordering and role-usage queries, soft delete, the partial unique indexes, the check constraints of the flow-node table and of the default flow, the message with its anchors, its answer and its fields stored as JSON, the versions, with one number per process and a whole diagram in the column — and the two that feed the cache, which answer which version is in force and what one says without crossing stores — and the named queries of the execution and of the two message trays, which do not exist as code: a renamed one does not start the application |
| Security and isolation (`@SpringBootTest`) | 253 | The two-store IDOR suite, one block of it for cases, tasks, their timeline and the message trays, read-only sharing (HU-23), the role matrix with the error body behind every `403` and `404`, JWT tampering and expiry, sessions, the login limit, idempotency keys, the last active administrator under concurrent changes, temporary passwords and the change they force, passwords that never reach a response, and end-to-end `401`, `403`, `429` and firewall `400` responses |
| Profiles, schema, queries, API contract and demo data (`@SpringBootTest`) | 46 | What `dev` and `prod` expose, where `prod` puts Actuator and how it writes its logs, what runs on its own in each one and what does not run in `test`, the size of the connection and thread pools, the Flyway migrations and unique indexes, SQL statement counts that catch N+1 queries and prove that the JWT filter runs no SQL, the OpenAPI contract, what Actuator publishes and to whom, the four gauges of the operation included, the demo data read through the API, and the cache of what is published: that the second read costs no query, that a version published or retired is seen at once, that a warm entry does not skip the permission check, and that Actuator counts the hits |
| Module integration (`@SpringBootTest`) | 145 | Deactivating a colleague and bringing them back, which is the round trip the users screen makes; process-role usage across modules, who sees which tray, the order of pools and lanes, the whole diagram, publishing into versions and the draft that goes ahead of them, the store history and the structure policy, optimistic locking on every edit, auditing, soft delete, the modeling history, the BPMN consistency rules, an order from opening to finishing through both trays, two people completing the same task at the same time, the four endings of the correlation of a message, the store's clock and what each tick delivers, and the whole demo order end to end: it arrives as a message, two people move it, the clock delivers what it sent and the partners answer, and it finishes shipped — twenty of them at a time, and every one of them cancelled instead when the rejection rate says so; and the dashboard over figures counted by hand, with a statement count that keeps it at six queries however many orders there are, and an order cancelled on purpose to prove that the cycle time counts only the ones that finished |
| Operations (unit, `@SpringBootTest` and a real server) | 38 | The request id: where it comes from, which incoming ids are refused, that it reaches the log and leaves it, and that every Problem Details carries it — from the API's handler, the security chain, the firewall, the idempotency filter, Spring's own exceptions and the server's error page, the last one through a real Tomcat whose filter throws; a 500 that never says what failed; the business counters, counted on commit and not on rollback, through a login, a publication and a whole case; and the management port with a real server, where the API port has nothing of Actuator, Prometheus and the probes answer on the other one and the metrics still ask for an administrator |
| Application context | 3 | The full context starts in the `test` profile, without the demo store and without the cache |
| PostgreSQL 16 (Testcontainers) | 79 | What only the production engine can answer: the partial unique indexes behind the name of a process and the pair of nodes of a flow, which H2 has to replace with a generated column, and the `text` columns that hold a published diagram, the variables of a case and the bodies of the messages. The migration, repository, version, publishing, execution, messaging, simulation and whole-demo suites run again here, unchanged, and the context starts with `validate`, so every entity is checked against the schema Flyway leaves behind |

The PostgreSQL row is the only one `./mvnw verify` does not run: it needs a Docker daemon, and a build that
depends on one is a build that breaks on the laptop of whoever does not have it. Those tests carry the
`postgres` tag, which the build excludes and this command runs on its own:

```bash
./mvnw test -Dsurefire.excluded.groups= -Dgroups=postgres
```

Without Docker they report as skipped instead of failing, and the pipeline runs them on every pull request.

Current coverage: 96 % of lines and 85 % of branches. The build fails below 85 % of lines or 70 % of
branches overall, and below 90 % and 80 % in the service packages, where the business rules live. The gate
leaves out DTOs and Spring configuration: they are records and wiring, and counting them only inflates the number.

## Mutation testing

Coverage says a line ran, not that a test would notice if it were wrong. PIT checks that: it changes the code of the
rules on purpose, one mutation at a time (a `>` that becomes `>=`, a condition turned around, a value that is no
longer returned) and runs the tests that cover it. A mutation no test notices is a rule without a real test behind it.

```bash
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

It mutates the services of the three modules, the engine among them, and the condition language, and runs their
own tests: the unit tests of each service, the engine's over JPA and the conditions'. Measured on 2026-09-30: 1660
mutations, 1259 of them caught (76 %), and 91 % of the ones those tests reach. The run fails below 75 %, so a new
rule has to come with its test. It takes about 50 minutes, so it is not part of `verify`: the pipeline runs it every
night on `main`, and on any branch from *Run workflow* in the Actions tab. The report is in `target/pit-reports`.

## Format

Spotless keeps imports in the groups the code already uses (static, `java`, `org`, `com` and the rest) and without
unused ones, and lines without trailing spaces. It only looks at the files a change touches against `origin/main`,
so the repository is never reformatted in one go:

```bash
./mvnw spotless:apply   # fixes what a change touched
./mvnw spotless:check   # what the pipeline runs
```

It does not impose a full formatter on purpose. Measured over today's code, Palantir Java Format rewrites 462 of the
519 files and a tuned Eclipse profile 146, because both reflow the lines by their own rules; these rules touch 39,
each for a real inconsistency.

Every push to `main` and every pull request runs the GitHub Actions pipeline:

| Job | What it checks |
|---|---|
| Build & Test | `./mvnw verify` on Ubuntu and Windows. The test results appear as a check, and the coverage report and the SBOM of the API are kept as artifacts. |
| Architecture Rules | The ArchUnit suite on its own, with a summary |
| PostgreSQL Integration | The suites tagged `postgres` against a PostgreSQL 16 container, the same image the Compose stack runs |
| Docker Image & Load Test | Builds the image and scans it with Trivy, checks that the API answers from the container, brings up the Compose stack in the `prod` profile against PostgreSQL 16, and runs the two k6 load tests against it: one that loads reading the model and one that loads running it |
| SonarCloud Analysis | Static analysis and its quality gate: the job waits for SonarCloud to judge the analysis and goes red when the gate does not pass. Skipped while the token is not configured |
| Frontend Build | The SBOM of the web app, `npm ci` and a production build |
| Web Image & Stack | Builds the web image and scans it with Trivy, then brings up database, API and web together: NGINX serves the compiled app, a deep link answers with the app instead of a `404`, and the API answers through the web container |
| E2E | Drives a headless Chrome against that stack with the Selenium suite of [e2e/](../e2e/README.md), and keeps what the browser saw as an artifact |
| Code Format | `spotless:check` on the files the change touches |
| Supply Chain | Every action is pinned to a commit SHA, and gitleaks finds no secret in the commits of the change |
| CodeQL | A separate workflow that reads the Java, the TypeScript and the workflows themselves, on every change and every Monday, and publishes what it finds in the Security tab |
| Mutation Testing | A separate workflow, every night and by hand: PIT over the rules, with the report kept as an artifact |

[Supply chain](security.md#supply-chain) explains what each of the last checks protects against.

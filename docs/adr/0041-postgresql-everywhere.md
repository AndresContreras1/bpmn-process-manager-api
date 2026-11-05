[← Decision records](README.md)

# ADR-0041: PostgreSQL in development and in the tests

- **Status:** Accepted
- **Decided in:** plan v5 (D41)
- **Supersedes:** [ADR-0021](0021-tagged-postgres-suite.md)

## Context

Until now `dev` and the build of every day ran on H2, and a suite tagged `postgres` repeated part of them against a
real PostgreSQL in a job of its own (ADR-0021). The schema had a branch per engine: what PostgreSQL writes as a
partial unique index, H2 had to fake with a generated column. Every change planned from here on uses something H2
does not have: row-level security, `LISTEN/NOTIFY`, `FOR UPDATE SKIP LOCKED`, full-text search. Keeping H2 would
mean a second branch for each of them, or features that are only ever tested in production.

H2 for development is what the course teaches; this decision goes against it on purpose.

## Decision

- `dev` runs on PostgreSQL 16 from `compose.dev.yaml`, which Spring Boot's Docker Compose support starts with the
  application and leaves running. Without Docker, the `DB_*` variables point at a server of one's own.
- The tests run against one PostgreSQL 16 for the whole run, which Testcontainers starts. The migrations run once,
  into a template database, and every Spring test context gets its own copy of it.
- Where Linux containers cannot run, as on GitHub's Windows runners, `PRUEBAS_POSTGRES_URL` points the run at a
  server that is already up.
- There is one set of migrations, written for PostgreSQL. H2 and its console are out of the build.

## Consequences

- Working on the project needs Docker running, for `dev` and for the tests.
- The suites that only repeated the H2 ones against PostgreSQL are gone: every suite runs there now. The job
  *PostgreSQL Integration* is gone with them.
- A test that relied on H2 continuing a transaction after an error had to be split: PostgreSQL aborts it.
- The AOT cache of the image trains without a database: Flyway does not run and Hibernate is told the dialect, so
  the context starts without connecting, and the start-up of the image stays as fast.

## Verification

- The whole build runs on PostgreSQL in the CI, on Ubuntu with Testcontainers and on Windows with the runner's own
  server.
- `EsquemaEnPostgresTest` checks the engine and its version, the partial indexes and the `text` columns.
- `SinH2Test` fails if H2 comes back to the classpath; `PerfilesTest` checks that `dev` brings up its own database.

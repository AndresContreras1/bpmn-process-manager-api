[← Decision records](README.md)

# ADR-0021: Testcontainers as a tagged suite

- **Status:** Superseded by [ADR-0041](0041-postgresql-everywhere.md)
- **Decided in:** plan v3 (D21)

## Context

The daily build runs on H2. Some things only PostgreSQL can answer: the partial unique indexes behind the name of a
process and the pair of nodes of a flow, the `text` columns that hold a published diagram, the case variables and
the message bodies, and the schema Hibernate validates at startup. A build that needs Docker breaks on every machine
without it.

## Decision

The test classes annotated `@ConPostgresReal` carry `@Tag("postgres")` and
`@EnabledIf("com.facimus.procesos.postgres.PostgresDePrueba#hayDocker")`. `PostgresDePrueba` starts `postgres:16`
with Testcontainers and points the datasource at it with `@ServiceConnection`, so Flyway applies
`db/migration/common` and `db/migration/postgresql`, partial indexes included, and Hibernate validates the result.

- The pom leaves the tag out of `./mvnw verify` (`surefire.excluded.groups`), and
  `./mvnw test -Dsurefire.excluded.groups= -Dgroups=postgres` runs it.
- Without Docker, those classes are skipped instead of failing.
- In CI they run in a job of their own.

## Consequences

- Nine of the tagged classes extend an H2 suite and run it unchanged on PostgreSQL: migrations, repositories,
  versions and publishing, execution, messaging, simulation and the whole demo.
- The containers run `postgres:16`, the version of the Compose stack, and the CI job passes `-DfailIfNoTests=true`,
  so an empty tag is a red job instead of a green one.
- Plan v5 (decision D41) will replace the tagged suite by running the whole suite against PostgreSQL.

## Verification

- CI job *PostgreSQL Integration*, on every push to `main` and every pull request.
- `EsquemaEnPostgresTest`: the context starts against PostgreSQL 16 and Hibernate accepts the schema.
- `MigracionesEnPostgresTest` and `BajaLogicaEnPostgresTest`: the partial unique indexes of the name of a process and
  of the pair of nodes of a flow.

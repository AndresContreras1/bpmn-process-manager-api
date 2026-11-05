[← Decision records](README.md)

# ADR-0022: Named queries where startup checks them

- **Status:** Accepted
- **Decided in:** plan v3 (D22)

## Context

The reads the operation depends on run on every task, message and tick. A mistake in one of them, a renamed field or
a misspelled name, should stop the application and its context test from starting, not fail a request in
production.

## Decision

The read queries of `ejecucion` are declared on their entity as `@NamedQuery`, and the repository method carries only
the name; Spring Data finds each one by `<Entity>.<method>`:

- `Caso.abiertosPorProceso` and `Caso.porReferencia`;
- `ActividadCaso.bandejaPorRol` and `ActividadCaso.bandejaDeMisRoles`, the trays of tasks;
- `MensajeSaliente.bandejaDeSalida` and `MensajeSaliente.vencidos`;
- `MensajeEntrante.bandejaDeEntrada` and `MensajeEntrante.pendientesDeLaTienda`.

The paged ones have a `.count` twin for the total. Hibernate parses them all when the persistence unit starts, and a
repository method left without its query stops the context. The rest of the queries stay in `@Query`.

## Consequences

- A query is read far from the method that runs it; the comment of each repository says where to look.
- The version in force is not a named query: it belongs to `gestion`, and `VersionProcesoRepository` reads it with a
  derived query and with `idDeLaVigente`, an `@Query` that reads the id without the diagram.

## Verification

- `ProcesosApplicationTests.contextLoads`: the whole context starts in the `test` profile.
- `CasoRepositoryTest` and `MensajeriaRepositoryTest`: every named query against the Flyway schema, with its
  filters, its paging and the store it is limited to.
- `EjecucionEnPostgresTest`, `SimulacionEnPostgresTest` and `DemoEnPostgresTest`: the same queries on PostgreSQL,
  where a parameter compared with `is null` needs a type and the enums of a query have to match the column.

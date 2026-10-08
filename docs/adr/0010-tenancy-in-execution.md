[← Decision records](README.md)

# ADR-0010: Multi-tenancy holds in execution too

- **Status:** Accepted
- **Decided in:** plan v3 (D10)

## Context

Every entity except the store itself carries `empresa_id`, every repository filters by it, and a resource of another
store answers `404`. Execution added new tables, and a store a process is shared with (HU-23) can read that process.

## Decision

`casos`, `actividades_caso`, `eventos_caso`, `mensajes_salientes`, `mensajes_entrantes`, `versiones_proceso`,
`configuracion_tienda` and `membresias_rol` carry `empresa_id`, through `EntidadEmpresa` like every other entity.

- The store of a case is the store of its process.
- Every lookup goes through `findByIdAndEmpresaId` or a query that names the store, the named queries included.
  Only the Actuator gauges count the whole installation.
- A guest store cannot open cases on a shared process: opening goes through the write door, which only finds the
  store's own processes, so it answers `404`.

## Consequences

- A case, a task, a timeline or a tray of another store does not exist for the caller.
- The clock and the partner settings of a store are its own row, so two stores of one installation can be at
  different points of their simulations.
- The cache keys start with the store too ([ADR-0019](0019-cache-only-the-immutable.md)).

## Verification

- `MultitenenciaTest.entidades_extienden_EntidadEmpresa`,
  `RepositorioTenantTest.repositorios_extienden_RepositorioTenant` and
  `AislamientoTenantTest.services_consultan_con_empresaId` apply to the new entities with no change.
- `AislamientoDeLaEjecucionTest`: with two stores, the cases, tasks, timelines and message trays of the other one
  answer `404` or come back empty, and a guest store cannot open a case on a process shared with it.
- `AislamientoEmpresasIntegracionTest`: the versions of another store's process answer `404`.

[← Decision records](README.md)

# ADR-0015: A history for the whole store

- **Status:** Accepted
- **Decided in:** plan v3 (D15)

## Context

The history recorded changes to processes and their models, and every entry needed a process. Creating a user,
changing a role or deactivating someone left nothing an administrator could read.

## Decision

`historial_cambios.proceso_id` became optional, and every entry says what it is about with `recurso_tipo`
(`PROCESO`, `ROL`, `USUARIO` or `EMPRESA`) and `recurso_id`. Users, process roles and the registration of the store
write there, with their author. `GET /api/v1/empresas/actual/historial` lists the store's whole history, newest
first and paginated, for administrators only. The history of a process, `GET /api/v1/procesos/{id}/historial`, did
not change.

## Consequences

- The entries written before the change were marked `PROCESO`, with their process as the resource, so none is left
  without one.
- Changes to the store's own settings ([ADR-0016](0016-structure-policy-per-store.md)) are recorded there too.
- The store-wide read has its own index, on `empresa_id` and `fecha_cambio`.

## Verification

- `HistorialDeTiendaIntegracionTest`: the registration of the store and of its first administrator; a colleague
  created, given another role and deactivated; a process role created, renamed and deleted; what happens in a process
  seen from the store; nothing of another store; and pages from newest to oldest.
- `AutorizacionPorRolTest`: the route answers only administrators.

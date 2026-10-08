[← Decision records](README.md)

# ADR-0016: A structure policy per store

- **Status:** Accepted
- **Decided in:** plan v3 (D16)

## Context

The role matrix lets editors create and edit participants and lanes. Some stores want the shape of their diagrams,
who takes part and which roles work in it, decided by administrators only. That is a fact about one store, and the
matrix in `SecurityConfig` is the same for all of them.

## Decision

`configuracion_tienda.politica_estructura` is `SOLO_ADMINISTRADOR` or `ADMINISTRADOR_Y_EDITOR`. The default is the
second, which is the matrix as it was. It decides who creates and edits pools and lanes; deleting them stays with the
administrator whatever the store chooses.

The check lives in the services, not in `SecurityConfig`: `PoolServiceImpl` and `LaneServiceImpl` call
`ConfiguracionTiendaService.exigirPuedeEditarEstructura`, which throws `SinPermisoException`, and the API's handler
answers `403` with the reason in `detail`. An administrator reads and changes the policy with `GET` and
`PUT /api/v1/empresas/actual/configuracion`.

## Consequences

- Under either policy, editors keep modeling everything inside a lane: steps, flows and messages.
- Every store got its row with `ADMINISTRADOR_Y_EDITOR`, the ones that existed before the change included.
- A `403` caused by the store's policy says why, unlike the generic `403` of the fixed matrix.
- Changing the policy checks the optimistic version and is recorded in the store's history.
- The same row later took the store's clock and the settings of its partners
  ([ADR-0008](0008-clock-moved-by-the-caller.md), [ADR-0007](0007-partners-behind-a-port.md)).

## Verification

- `ConfiguracionTiendaIntegracionTest`: with `SOLO_ADMINISTRADOR` an editor cannot create pools or lanes and an
  administrator can (R-46); a new store lets editors draw the structure; one store's policy does not reach another.
- `AutorizacionPorRolTest`: the settings route answers only administrators.

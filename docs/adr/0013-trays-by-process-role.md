[← Decision records](README.md)

# ADR-0013: Trays by process role; memberships are optional

- **Status:** Accepted
- **Decided in:** plan v3 (D13)

## Context

HU-02 and HU-17 ask to keep the access role, what someone may do in the API, apart from the process role, whose work
a task is.

## Decision

A task is born with the process role of its lane (`actividades_caso.rol_proceso_id`), and
`GET /api/v1/tareas?rolProcesoId=…` is the tray of that role. Any `ADMINISTRADOR` or `EDITOR` of the store can
complete or take a task (`POST /api/v1/tareas/{id}/completar`, `POST /api/v1/tareas/{id}/asignar`), and every
access role can read the trays.

As an optional layer, `membresias_rol` relates users to process roles, one row per pair. An administrator manages
it with `GET` and `PUT /api/v1/usuarios/{id}/roles-proceso`, and `GET /api/v1/tareas?mias=true` answers only the
tasks of the caller's roles. `RolAcceso` did not change.

## Consequences

- A membership filters and grants nothing: someone with no roles gets an empty tray of their own, and can still
  complete any task their access role allows.
- A store that does not want to manage memberships never sets any, and the tray by role keeps working.

## Verification

- `AutorizacionPorRolTest`: the role matrix with the task routes, `?mias=true` and the membership routes.
- `BandejaPropiaIntegracionTest`: each person sees the tasks of their roles, someone with none sees nothing and still
  completes a task, and replacing the roles changes the tray at once.
- `MembresiaRolRepositoryTest` and `MembresiaRolServiceTest`.

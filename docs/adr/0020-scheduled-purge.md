[← Decision records](README.md)

# ADR-0020: A scheduled purge of technical rows

- **Status:** Accepted
- **Decided in:** plan v3 (D20)

## Context

Three tables only grow: a login writes a session, every renewal writes a refresh token, and every request with an
`Idempotency-Key` writes a key. Nobody reads them back once they expire. Everything else in the API is soft-deleted
and stays.

## Decision

`LimpiezaConfig` runs `LimpiezaService.limpiar()` on `LIMPIEZA_CRON` (`0 30 3 * * *` by default; `-` turns it off).
It deletes:

- refresh tokens that expired more than `LIMPIEZA_RETENCION_SESIONES` ago (seven days by default);
- sessions older than that window with no refresh token left. The window is never shorter than the access token
  lifetime, because a restart rereads the recently closed sessions to keep rejecting their tokens;
- idempotency keys older than `LIMPIEZA_RETENCION_IDEMPOTENCIA` (24 hours by default).

Tokens go first, so a session left without tokens goes in the same pass. The job is `@Profile("!test")`: tests call
the service.

## Consequences

- It is the only physical `DELETE` in the API, on technical tables, and the only place that crosses stores on
  purpose: it reads no store's data.
- Since PR 35 it also deletes the stores whose closing is 30 days old, row by row (`BorradoDeTiendas`): the one
  time it touches a store's own data, and only that of a store that asked to go.
- Messages are not purged: the trays keep every message a store sent or received.
- A deployment that prefers to sweep from outside sets `LIMPIEZA_CRON=-`.

## Verification

- `LimpiezaIntegracionTest`: against the database, a row on each side of every limit, and a session left without
  tokens that goes in the same pass.
- `LimpiezaServiceTest`: the limit of each table, the order, and a session never purged while an access token of it
  can still be alive.
- `PerfilesTest` and `EmpaquetadoTest.lo_que_corre_solo_vive_en_config`: the job exists in `dev` and not in `test`,
  and lives in `config`.

[← Decision records](README.md)

# ADR-0023: Optional shared state

- **Status:** Not implemented; superseded by [ADR-0034](0034-postgresql-for-shared-state.md)
- **Decided in:** plan v3 (D23)

## Context

Some state lives in the memory of one instance: the sessions closed recently (`RevokedSessions`), the failed login
attempts and the per-store limit of AI reviews (`AttemptLimiter`), and the last review of each process
(`UltimasRevisiones`). With one instance that is enough. With several, each would keep its own: a session closed
through one would keep its access token working on another until the token expired, and every limit would count per
instance.

## Decision

If `REDIS_URL` were defined, closed sessions, login attempts and reviews would be kept in Redis; if not, in memory
as before. It only made sense with more than one instance, so it was planned as an optional change (PR 28).

## Consequences

- It was never built. There is no Redis client in the build and no `REDIS_URL`; the state above stays in memory,
  closed sessions are reread from the database on startup, and the Compose stack runs a single API instance.
- Plan v5 replaces it with D34: PostgreSQL holds all shared state, and Redis is not used. Its record arrives with
  that change.

## Verification

- Nothing checks this decision, because nothing implements it.
- `RevokedSessionsTest` and `AttemptLimiterTest` cover the in-memory versions that stay.

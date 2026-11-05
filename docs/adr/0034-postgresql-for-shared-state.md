[← Decision records](README.md)

# ADR-0034: PostgreSQL for all shared state

- **Status:** Accepted
- **Decided in:** plan v5 (D34)
- **Supersedes:** [ADR-0023](0023-optional-shared-state.md)

## Context

Some state lived in the memory of each instance: the sessions closed recently, the failed logins, the last AI
review of each process and the count behind the store's limit of reviews. With two instances, a session closed in
one kept working in the other until its token expired, every limit counted per instance, and both instances ran
the nightly purge and moved the simulation clock. Plan v3 left a Redis store as an option (ADR-0023) that was never
built; plan v5 needs a job queue, an outbox, presence and locks, and every one of them would ask the same question.

## Decision

PostgreSQL holds everything the instances share, and Caffeine what each one may keep to itself. There is no Redis.

- Closed sessions stay in the memory of each instance, so the JWT filter runs no SQL, and every closure is
  announced with `NOTIFY` inside the transaction that makes it. Each instance listens on a connection of its own.
- Failed logins are rows of `intentos_login`, and AI reviews rows of `revisiones_ia`.
- Every scheduled job takes its lock in `shedlock` (ShedLock, with the database's clock) before it runs.

## Consequences

- One service less to run, back up and secure; the database the product already needs does the job.
- A notice that arrives while an instance is not listening is lost, so each instance rereads the recent closures
  when it reconnects and every five minutes.
- A failed login costs a few queries more, and the limits are exact only up to requests that arrive at the same
  time.
- The tables that only grow are swept by the nightly purge (D20).

## Verification

- `DosInstanciasTest` starts two instances on one database: a session closed in one stops working in the other,
  failures in one lock the email in the other, a review asked in one comes back from the other, and the lock of
  a job taken by one is not taken by the other.
- `IntentosDeLoginTest` and `RevisionIaRepositoryTest` check the windows and the purge against PostgreSQL;
  `AvisoDeSesionesCerradasTest` the notices.
- `EmpaquetadoTest.lo_que_corre_solo_toma_su_candado` fails the build if a `@Scheduled` method has no
  `@SchedulerLock`, and `PerfilesTest` checks that each job takes its lock.

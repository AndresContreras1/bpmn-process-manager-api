[← Decision records](README.md)

# ADR-0068: Backups with pgBackRest and a monthly restore drill

- **Status:** Accepted
- **Decided in:** plan v5 (PR 67)

## Context

Before the product keeps real customer data, losing the database has to cost minutes of work at most, and a backup
has to be something that was restored and checked, not a file nobody opened. Until now the runbook had a `pg_dump`
taken by hand. The audit trail and the fingerprints of the published versions already say whether data was changed;
a restore should be checked against them, not only against "it started".

## Decision

- **pgBackRest, in the database image.** `ops/respaldo/Dockerfile` is the pinned `postgres:16` image plus pgBackRest,
  curl and jq from the distribution's packages, running as `postgres` (gosu is removed). PostgreSQL archives every WAL
  segment with `archive_command = pgbackrest archive-push`, and `archive_timeout = 60s` closes a segment at least
  every minute when something was written. A `respaldo` service of the same image, sharing the data volume read-only
  and the socket, takes a full backup on Sundays and a differential on the other days from 01:00 UTC; the first one as
  soon as it starts, and one at once whenever the last is older than 26 hours. A loop in `sh` and not cron: it catches
  up after downtime and adds no binary to pin and scan.
- **The repository is a bucket with Object Lock,** in compliance mode with a default retention of 21 days (two weekly
  fulls and one week of margin), created by the backup service itself with a signed request; the API's credentials
  and code are not involved. Every file is encrypted with AES-256-CBC by pgBackRest before it leaves, with
  `RESPALDO_CLAVE`, a required secret. In `compose.yaml` the bucket lives in the stack's SeaweedFS; pgBackRest only
  speaks S3 over TLS, so SeaweedFS also listens with HTTPS on 8334, with a self-signed certificate that a one-shot
  service creates and that pgBackRest trusts as its only authority. In AWS the bucket is in another account, reached
  with other keys (`RESPALDO_S3_*`), so a compromise of the production account cannot delete it.
- **Two fulls are kept** (`repo1-retention-full=2`): any point of the last seven to fourteen days can be restored.
- **Each backup records its rows.** After a backup, every table of the schema is locked in `SHARE` mode (writes wait,
  reads go on) for the time of one counting statement, a named restore point is created in the same transaction, and
  the counts and the point become annotations of that backup in the repository. Restoring up to that point must give
  exactly those rows. The locks are taken with `NOWAIT`, so the count never waits and never deadlocks with a
  migration or a transaction of the API: a busy table makes it let go and try again, and after ten tries the backup
  stays without its count.
- **The documents' bucket keeps versions.** The API turns versioning on when it prepares its buckets
  (`ALMACEN_S3_CREAR_BUCKETS`), with a rule that expires a replaced or deleted version after 35 days: a document
  overwritten or deleted, also by the purge of a closed store, can be brought back within that time and is gone
  after it. In AWS, replication to a bucket of another account is configuration, described in the runbook.
- **The drill restores into an empty container** (`restauracion`, profile `simulacro`) and checks three things: the
  rows of every table against the annotations, every audit trail against its chain and its latest anchor, and every
  published version against its fingerprint. The last two run the API's own code, not a copy of it in SQL: the API
  image starts with `RESPALDO_VERIFICAR=true` and no web server against the restored database, verifies every store
  as the system, writes what it found and ends, failing its start if anything does not match. Rewriting the hash of
  the audit trail or the canonical form of a diagram in SQL would have been a second implementation to keep equal to
  the first; reading a stored definition the way the API reads a published version cannot drift.
- **The drill measures the RPO and the RTO.** The RPO is how long the WAL position of the start of the drill takes to
  reach the repository; the RTO, from the start of the restore until everything is verified.
- **A workflow runs the drill every month** (`restore-drill.yml`), on a stack that the order peak filled through the
  API, both up to the restore point of a fresh differential backup and up to the end of the archived WAL.

## Consequences

- The RPO is under a minute plus the time of a push (measured: see the runbook); the RTO of the stack's database is
  a few minutes. Both proposals of the plan (15 minutes, 4 hours) hold with room; a bigger database moves the RTO.
- The database image is built from the repository and scanned by Trivy like the other two; Dependabot bumps its base.
- `RESPALDO_CLAVE` is the key of the whole repository: losing it loses every backup, and another key is another
  repository. It is kept apart from the machine, like the other secrets.
- `docker compose down -v` deletes the backups of the local stack too, because its bucket lives in the stack's own
  storage volume. The protection against losing the machine is the bucket of another account.
- Noncurrent versions in the local SeaweedFS expire only as far as SeaweedFS applies lifecycle rules; AWS applies
  them as configured.

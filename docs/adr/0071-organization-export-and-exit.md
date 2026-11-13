[← Decision records](README.md)

# ADR-0071: Organization export and exit

- **Status:** Accepted
- **Decided in:** plan v5 (PR 66)

## Context

A customer asks, before buying, how it leaves: whether it can take everything it put in, in formats other tools
read, and what proof it gets that the rest was deleted. Closing a store (PR 35) deleted every row after 30 days of
read-only grace, but gave nothing to take away and left no record of what went.

## Decision

- **One ZIP with everything, built in the job queue.** `POST /api/v1/organizacion/exportaciones` leaves the export
  `PENDIENTE` and a job; the job writes the ZIP entry by entry to a file of `ArchivosTemporales`, so nothing is held
  whole in memory, and stores it in the object storage of ADR-0065 under the store's prefix. Big tables are read 500
  rows at a time by id. No transaction is held while it builds: the job reads the request and notes the result in
  two short transactions, like the manual of ADR-0066.
- **Each module writes its own part.** `gestion` owns the export, its ZIP and its manifest, and declares the port
  `ParteDeLaExportacion`; `gestion`, `modelado` and `ejecucion` implement it with their own tables, as with
  `InstantaneaDelModelo`. The job pins the store in `ContextoDeTienda`, so row-level security refuses any row of
  another store even if a query forgot to filter.
- **Columns are named, never `*`.** Every query of the export lists its columns: what is not named does not leave.
  People leave without password hashes, WebAuthn handles, TOTP secrets, recovery codes, passkeys, sessions, refresh
  tokens or e-mail links; those tables are not read at all. Storage keys are used to read files and not exported.
- **A stable, versioned layout.** `manifest.json` lists every file with its size and SHA-256, the store, who asked,
  when, and `formato: 1`; the ZIP's own SHA-256 is kept with the export and sent in the e-mail. Paths are fixed by the
  code; anything a person wrote (a document's name) passes through a sanitizer before entering a path, and the
  writer rejects any path with `..`, an absolute path, a hidden or repeated one, or a file over a folder. The layout
  is documented in [Export format](../export-format.md).
- **Verifiable outside the API.** The audit trail leaves with every field that enters each hash, in chain order,
  with its anchors and the anchor files read from the immutable storage, so the chain can be recomputed with any JSON
  library. Cases leave as JSON and as RFC 4180 CSV, where a text that starts with `=`, `+`, `-`, `@`, a tab or a
  carriage return gets a leading apostrophe; numbers stay numbers.
- **Delivery by a signed link that lasts 24 hours.** Only the administrator asks, with a recent verification of the
  identity (the step-up of PR 49, the same as closing the store, see [Security](../security.md#second-factor)), and
  at most ten times a day per person. The link (`EnlacesFirmados`, signed up to the export's expiry) goes by e-mail
  through the job queue to whoever asked, in their language, and nowhere else: listing exports shows their state,
  size and hash, never the link. Asking, ready, failed, downloaded and expired go to the audit trail. One export runs
  per store at a time, checked with the store's row locked and guaranteed by a partial unique index.
- **Expiry deletes.** An hourly job (ShedLock) deletes from the storage each export whose link expired and marks it
  `VENCIDA`, keeping its row and hash as a record; a pending export that the queue did not finish in six hours is
  marked `FALLIDA` so another can be asked for.
- **Exit: an option, not a precondition.** Closing the store takes `exportar: true` to ask for the export in the same
  transaction, and the 30 days of grace allow asking for more exports: besides cancelling the closing, the only
  change the read-only store accepts.
  Requiring an export before closing would block a customer who already has one or wants none.
- **A deletion certificate after the purge.** The nightly purge counts the rows it deletes per table and reads the
  hash of the last ready export before deleting; the storage job then deletes the store's prefix, counting objects,
  and writes a JSON certificate: the store's id (no name, NIT or e-mail), when its closing was asked for and when it
  was deleted, rows per table, objects, the last export's hash, and what is kept (the store's tombstone row, its audit
  trail and the trail's anchors). It goes to the immutable storage beside the anchors (`certificados/<id>/borrado`,
  Object Lock in S3, ten years by default), and its SHA-256 to the store's audit trail as `TIENDA_BORRADA`. Once
  issued, a retry of the job does not issue another.

## Consequences

- The certificate lives where the operator can reach it, not the customer: once the store is gone nobody can sign in
  to it. The former administrator asks the operator, quoting the store id, which every export manifest carries;
  whoever holds a copy checks it against the hash in the audit trail.
- An export is not a single database snapshot: rows written while it builds may or may not be in it. Each row is as
  it was when it was read, and the audit trail's chain verifies whatever part of it the export holds.
- Documents blocked by the antivirus or still unchecked are listed but their files do not leave; deleted documents
  do not leave at all.
- Reading anchors needed `Almacen.abrirInmutable`: in S3 they live in the Object Lock bucket, which `abrir` does not
  read. The audit trail's verification now reads them from there too.
- The ZIP is not encrypted and does not count against the document quota: it lives the 24 hours of its link and at
  most one more, until the hourly cleanup deletes it.

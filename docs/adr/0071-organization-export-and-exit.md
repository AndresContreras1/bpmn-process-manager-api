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
- **Every attempt uploads to its own folder.** The queue reopens a job still running after its stuck threshold
  (15 minutes), and a big store can take longer, so two attempts may build the same export at once. Each one uploads
  to `exportaciones/<id>/<attempt>/organizacion`, and noting the result locks the export's row: the first to arrive
  marks it ready with its key and hash, and a late one finds it no longer pending and deletes only its own folder.
  The hourly cleanup locks the row the same way before expiring or failing it. Whatever marks an export `FALLIDA`
  deletes its whole folder while it holds the lock: an earlier attempt may have uploaded a ZIP and died before noting
  it, and nothing else would delete that file before the store is purged.
- **Each module writes its own part.** `gestion` owns the export, its ZIP and its manifest, and declares the port
  `ParteDeLaExportacion`; `gestion`, `modelado` and `ejecucion` implement it with their own tables, as with
  `InstantaneaDelModelo`. The job pins the store in `ContextoDeTienda`, so row-level security refuses any row of
  another store even if a query forgot to filter. One exception: the cases' part joins `versiones_proceso` to write
  the version number instead of its internal id, reading only that column.
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
  carriage return gets a leading apostrophe; numbers stay numbers. The cell is `common/csv/CeldaCsv`, the same one
  the audit trail's CSV uses.
- **Delivery by a signed link that lasts 24 hours.** Only the administrator asks, with a recent verification of the
  identity (the step-up of PR 49, the same as closing the store, see [Security](../security.md#second-factor)), and
  at most ten times a day per person. The link (`EnlacesFirmados`, signed up to the export's expiry) goes by e-mail
  through the job queue to whoever asked, in their language, and nowhere else: listing exports shows their state,
  size and hash, never the link. The link belongs to an active administrator: the e-mail does not go, and the link
  stops downloading, once whoever asked is deactivated or loses the role. Asking, ready, failed, downloaded and
  expired go to the audit trail; a failure records a fixed reason, never the exception's message, which can carry
  SQL or bucket names and goes to the log. One export runs per store at a time, checked with the store's row locked
  and guaranteed by a partial unique index.
- **The download writes the audit trail from a GET.** Every other GET of the API is read-only, but who downloaded a
  whole store must be on record. The route checks the signature first and writes one entry through `Auditoria`, in
  its own short transaction.
- **Expiry deletes.** An hourly job (ShedLock) deletes from the storage each export whose link expired and marks it
  `VENCIDA`, keeping its row and hash as a record; a pending export that the queue did not finish in six hours is
  marked `FALLIDA` so another can be asked for.
- **Exit: an option, not a precondition.** Closing the store takes `exportar: true` to ask for the export in the same
  transaction, and the 30 days of grace allow asking for more exports: besides cancelling the closing, the only
  change the read-only store accepts. With one already being built the closing goes on and does not ask for another:
  failing the closing over an export that is already coming would only make the customer try again.
  Requiring an export before closing would block a customer who already has one or wants none.
- **A deletion certificate after the purge.** The nightly purge counts the rows it deletes per table and reads the
  hash of the last ready export before deleting; the storage job then deletes the store's prefix, counting objects,
  and writes a JSON certificate: the store's id (no name, NIT or e-mail), when its closing was asked for and when it
  was deleted, rows per table, objects, the last export's hash, and what is kept (the store's tombstone row, its audit
  trail and the trail's anchors). It goes to the immutable storage beside the anchors (`certificados/<id>/borrado`,
  Object Lock in S3, ten years by default), and its SHA-256 to the store's audit trail as `TIENDA_BORRADA`. Once
  issued, a retry of the job does not issue another, but it checks the trail: if the earlier attempt stored the
  certificate and failed before auditing it, the retry audits the hash of the stored one. A job queued before this
  change carries only the store's id; it still deletes the files, and its certificate says the rows were not counted.

## Consequences

- The certificate lives where the operator can reach it, not the customer: once the store is gone nobody can sign in
  to it. The former administrator asks the operator, quoting the store id, which every export manifest carries;
  whoever holds a copy checks it against the hash in the audit trail.
- An export is not a single database snapshot: rows written while it builds may or may not be in it. Each row is as
  it was when it was read, and the audit trail's chain verifies whatever part of it the export holds.
- Documents blocked by the antivirus or still unchecked are listed but their files do not leave; deleted documents
  do not leave at all. A document version or manual whose file the storage lost is listed as missing (`faltante`,
  `faltantes`) and the export goes on: a store that lost one object can still take the rest with it.
- Reading anchors needed `Almacen.abrirInmutable`: in S3 they live in the Object Lock bucket, which `abrir` does not
  read. The audit trail's verification now reads them from there too. An anchor stored before the Object Lock bucket
  was configured is still in the documents' bucket, so a key missing from the lock bucket is read from there.
- A certificate counts the objects its own attempt deleted. If an earlier attempt deleted the files and failed before
  storing the certificate, the count is lower than what went; the certificate says which attempt issued it.
- Two attempts that build the same export both do the work; only one result is kept.
- The ZIP is not encrypted and does not count against the document quota: it lives the 24 hours of its link and at
  most one more, until the hourly cleanup deletes it.

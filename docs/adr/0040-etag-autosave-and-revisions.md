[← Decision records](README.md)

# ADR-0040: ETags, autosave of the whole file, and draft revisions

- **Status:** Accepted
- **Decided in:** plan v5 (PR 40, D27)

## Context

The BPMN editor (F15) saves the whole diagram as XML every couple of seconds. Two tabs must not overwrite each other, a
retry after a network cut must not apply twice, a save that changes nothing must not write, and people need to go back
to how a draft was without publishing.

## Decision

- **HTTP says what the `version` field says.** Any record with a `version` carries it in `ETag` on `GET`, and `If-Match`
  is accepted next to the `version` of the body: when they disagree, or the version is old, the answer is `412` instead
  of `409`. Clients that only send the body keep getting `409`.
- **The draft's fingerprint is the SHA-256 of the file it exports**, not the fingerprint of its versions. That one names
  database rows, and restoring creates new rows; the file only names `bpmn_id`s, so restoring a revision gives back its
  fingerprint.
- **`PUT /procesos/{id}/modelo` is the autosave.** It locks the process row, so two saves go one at a time; compares
  `If-Match` and answers `412` with the current fingerprint and the latest changes; and writes nothing when the file
  changes nothing, not even an import record. `Idempotency-Key` now also applies to this `PUT`.
- **`revisiones_borrador`** keeps compressed save points: every few minutes of editing (`modelado.revisiones.intervalo`,
  5 minutes), on closing the editor, and just before restoring, so a restore can be undone. The newest
  `modelado.revisiones.maximo` per process are kept.
- **A revision is not a version (D2):** it is never published and opens no cases.

## Consequences

- The file a browser sends is formatted differently from the one exported, so a save that changes nothing is detected
  after converting it: nothing is written, but the file is read.
- A restore follows the permissions of importing a file: deleting needs an administrator.

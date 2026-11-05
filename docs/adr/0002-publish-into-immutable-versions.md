[← Decision records](README.md)

# ADR-0002: Publishing creates an immutable version; the live model is the draft

- **Status:** Accepted
- **Decided in:** plan v3 (D2)

## Context

A `PUBLICADO` process still accepted changes to its model, since no modeling service looked at `EstadoProceso`, and
a case running over rows that change under it is a corrupt case. Cloning the process and its model for each version
was dropped: it breaks the unique name per store, multiplies rows, and the viewer would have to pick a copy.

## Decision

`PATCH /api/v1/procesos/{id}` with `estado: PUBLICADO` publishes. It runs the diagnosis, and with a single error it
answers `409` with the list and saves nothing. Otherwise it stores the next number in `versiones_proceso` with a
snapshot of the diagram, the same JSON that `GET /api/v1/procesos/{procesoId}/diagrama` returns, and the SHA-256
fingerprint of its canonical form.

- The live model stays editable: it is the draft. A process read on its own answers `borradorPendiente: true` when
  the fingerprint of the live model differs from the published one.
- Publishing again creates the next version. Publishing with nothing changed answers `409`.
- An administrator retires a version with `PATCH /api/v1/procesos/{procesoId}/versiones/{numero}` and
  `{"estado": "RETIRADA"}`. New cases open on the newest version still standing, and the cases opened on the retired
  one keep running on it. A version is never edited or deleted, and its number is never reused.

## Consequences

- The web app's contract did not change: publishing is the same `PATCH`, and a published process still never goes
  back to draft.
- A guest store (HU-23) reads the version in force, not a half-edited draft.
- A case keeps the version it was opened on, so editing the model never moves the ground under it.
- A version never changes, which makes it the one thing worth caching ([ADR-0019](0019-cache-only-the-immutable.md)).
- The fingerprint covers every field of every element and the name, description and category of the process. It
  leaves out who saved, when, and the optimistic version, so saving without a change changes nothing.

## Verification

- `VersionesDeProcesoIntegracionTest`: a process with diagnosis errors answers `409` and leaves no version (R-44);
  editing after publishing leaves the draft pending and the version untouched; publishing twice without a change
  creates nothing (R-45); retiring, and numbers that are not reused.
- `HuellaDelDiagramaTest`: the fingerprint changes with a renamed activity or process, a moved activity, a changed
  condition, or one message, lane or correlation less, and not with the row order, the dates or the version.
- `VersionesDeProcesoEnPostgresTest` runs the same integration suite on PostgreSQL.

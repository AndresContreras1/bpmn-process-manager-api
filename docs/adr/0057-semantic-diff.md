[← Decision records](README.md)

# ADR-0057: Comparing processes by meaning

- **Status:** Accepted
- **Decided in:** plan v5 (PR 57)

## Context

A review, the history and the conflict notice all need to say what changed between two states of a process. Comparing
the XML says too much: a re-ordered attribute is a change. `bpmn-js-differ` is JavaScript and would keep the answer out
of the API.

## Decision

- **Compare in Java over the JSON of the snapshots**, the same form as the fingerprint: what the fingerprint ignores is
  not a change here either.
- **Elements are matched by `bpmn_id`, and references are compared by the `bpmn_id` they point to.** Database ids say
  nothing between two moments: restoring creates new rows.
- **Four lists:** added, removed, changed (with each field before and after) and moved (only place or size). A boundary
  event or a sub-process with content has no meaningful position, so it never counts as moved.
- **A file is compared by applying it for real in a transaction that is rolled back.** Nobody duplicates the import
  rules, and what the comparison says is what saving would do.

## Consequences

- `GET /procesos/{id}/comparar?de=&a=` takes a version number, `vigente` or `borrador`.
- `POST /procesos/{id}/comparar` takes a file: what someone sees after a `412`.

[← Decision records](README.md)

# ADR-0055: Descriptive BPMN elements, and a process that only documents

- **Status:** Accepted
- **Decided in:** plan v5 (PR 55)

## Context

ADR-0054 left out what the model did not have: timers, boundary events, annotations, groups, data objects and data
stores. Those are the elements of the descriptive conformance subclass of BPMN 2.0.2, the one most modelling tools
draw. A process can be worth modelling without ever running, and the engine does not run timers yet. Two questions
follow: where those elements live, and what the diagnosis asks of a process that will not run.

## Decision

- **A process has a mode.** `procesos.modo` is `EJECUTABLE`, what every process was until now, or `DOCUMENTAL`: it is
  modelled, published and documented, and opens no cases. `PUT /procesos/{id}/modo` changes it and answers the
  diagnosis of the new mode. The mode is part of the model: it counts in the fingerprint and takes effect when the
  process is published, so a published executable version keeps opening cases while its draft is documental. The
  execution reads it from the version's snapshot, and a snapshot without it is executable.
- **The diagnosis depends on the mode.** Each code says whether it is structural. A documental process counts only
  those: `E-09`, `E-12`, `A-06`, `A-10`, `A-13` and `A-15`. An executable one counts everything, plus `E-16` (a timer
  without a valid ISO 8601 expression), `E-17` (a task without a kind) and `E-18` (a timer, because timers do not run
  until PR 68). `A-15` is an association with an end that is gone.
- **New event and task kinds in the existing tables.** Timer start, catch and boundary events, the terminate end and
  the task without a kind are new values of the existing enums, with four nullable columns in `nodos_flujo`. The
  terminate end is the only one that runs: it closes every token of the case, tasks in the inbox included.
- **A boundary event belongs to its activity.** It sits in its activity's lane, moves and is deleted with it, receives
  no flows, and is drawn on the activity's bottom edge whatever its own position says. An activity with boundary
  events does not move to another pool.
- **Artifacts and associations have their own tables**, because they are not flow nodes: `artefactos` and
  `asociaciones`. An artifact sits in a lane, with a position relative to it like a node, or stands loose and is drawn
  below the pools. A data object or store needs a pool modelled inside, because BPMN declares data in a process.
  Association ends are `bpmn_id`s, since they can be nodes, flows or artifacts; an association goes when either end
  goes.
- **The fingerprint does not move for what already existed.** The new fields enter the canonical form only when they
  say something: no timer, no artifacts, executable. Otherwise every published version would suddenly look changed.
- **On export and import**, a documental process is `isExecutable="false"`; an input association reaches a
  `__targetRef_placeholder` property of its task, as bpmn.io writes it; a group's name is a `categoryValue` at the
  root. A file with `isExecutable="false"` is imported as documental, and one that does not say leaves the draft as
  it is. A plain `task` is now a task without a kind instead of a user task.

## Consequences

- Most files drawn in bpmn.io say `isExecutable="false"`, so they arrive documental and publish as they are.
- A process with a timer cannot be published as executable until PR 68: the diagnosis says so instead of letting a
  case pass through a wait that never waits.
- Only timer boundary events are imported. Error, escalation and the other kinds are left out and reported.
- The current web keeps drawing what it knew and preserves timer data when it saves; drawing and editing the new
  elements comes with bpmn-js (F15, F16).

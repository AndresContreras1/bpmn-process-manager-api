[← Decision records](README.md)

# ADR-0056: Sub-processes as containers, and call activities between processes

- **Status:** Accepted
- **Decided in:** plan v5 (PR 56)

## Context

Most models past a certain size use sub-processes, and a store's processes call each other. The engine runs neither
yet (call activities arrive with PR 73), but the model has to hold them, draw them, and go to and from BPMN.

## Decision

- **A sub-process is an activity that contains nodes:** `nodos_flujo.padre_id`. Flows do not cross its border; what is
  inside sits in its lane and moves and is deleted with it. A node with flows does not change container.
- **Two sub-process kinds and one call activity**, as new `TipoActividad` values: `SUBPROCESO` (expanded),
  `SUBPROCESO_COLAPSADO` (no content drawn) and `LLAMADA`, with `proceso_llamado_id` pointing to a process of the same
  store.
- **An expanded sub-process has no position of its own:** it is the box of what it contains, computed by the layout.
- **One plane.** The model draws a single diagram, so a collapsed sub-process that brings content in a file is expanded
  on import, and the report says so.
- **By mode:** a documental process accepts all three. An executable one rejects sub-processes (`E-19`, suggesting to
  convert them to a called process) and requires a call activity to name its process (`E-20`); while calls do not run
  a warning says so (`A-16`), and the case passes through.
- **Circular calls are checked when publishing** (`E-21`), because the graph spans processes and the diagnosis of one
  diagram does not see the others. Calling a deleted process is checked there too.
- **In BPMN**, `calledElement` is written `Proceso_<id>`. A call activity from another tool that names something else
  is imported without a process, and the report says so. An id of another store is ignored.

## Consequences

- An event sub-process (`triggeredByEvent`) is left out with its content.
- `PUT /nodos/{id}/contenedor` moves a node in or out, and `PUT /actividades/{id}/llamada` chooses the process: the
  existing create and edit requests did not change.

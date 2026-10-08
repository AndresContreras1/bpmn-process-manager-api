[← Decision records](README.md)

# ADR-0004: The state of a case is its steps, not a table of tokens

- **Status:** Accepted
- **Decided in:** plan v3 (D4)

## Context

BPMN engines speak of tokens. A table of tokens is exact, and unreadable for whoever opens a case to find out why it
is where it is.

## Decision

`actividades_caso` keeps one row each time a case goes through a node (an activity, a gateway or an event), with the
node's id and name copied from the version, its kind, the process role of its lane and a state:

| State | Meaning |
|---|---|
| `PENDIENTE` | Ready for the engine to process in the same transaction |
| `EN_ESPERA` | Waiting for a person, a message, or the other tokens of a join |
| `COMPLETADA` | Done |
| `FALLIDA` | A send that failed and could not be handled |
| `OMITIDA` | Still alive when the case was cancelled or finished |

The live tokens are the `PENDIENTE` and `EN_ESPERA` rows, and a join counts what has reached it in `llegadas`.
`eventos_caso` is the timeline: every decision, task, send, receipt and error, with its tick, date, type and author.
Its rows are only inserted.

## Consequences

- The detail of a case, `GET /api/v1/casos/{id}`, is the list of its steps, and `GET /api/v1/casos/{id}/eventos` is
  a timeline a person can read.
- The tray of tasks is a query over `actividades_caso`: activities of type `USUARIO` in `EN_ESPERA`.
- A step names its node by id, without a foreign key: the live model can delete the node later and what the case
  went through stays true.

## Verification

- `CasoRepositoryTest`, a `@DataJpaTest` on the Flyway schema: the tray brings only user activities in `EN_ESPERA`,
  filters by role and by process, pages, and never crosses stores; the arrivals of a join are stored and read back;
  the live tokens are the pending and the waiting rows.
- `MotorDeProcesosTest`: the engine wired by hand over a JPA slice and run on graphs built in memory.

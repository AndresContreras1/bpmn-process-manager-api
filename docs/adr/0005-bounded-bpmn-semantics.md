[← Decision records](README.md)

# ADR-0005: A bounded BPMN semantics, written down

- **Status:** Accepted
- **Decided in:** plan v3 (D5)

## Context

To run a diagram, each node has to mean one thing. BPMN is large, and two of its parts are hard to run: joins, and a
gateway where no condition holds. The modeling rules asked for a condition on every flow leaving an exclusive or
inclusive gateway, and said nothing of what happens when none holds.

## Decision

The table of what each node does is in [Execution and simulation](../execution.md#running-a-process), and the engine
follows it. The rules that needed a decision:

- A parallel join waits until as many tokens have arrived as there are flows coming in.
- An inclusive join waits while any other live token of the case can still reach it, worked out on the graph of the
  version.
- An exclusive gateway takes the first flow whose condition holds, in the order of its flows; an inclusive one takes
  every flow that holds.
- When none holds, a gateway that decides takes its default flow: `Arco.porDefecto`, at most one per gateway, only
  on a gateway that decides, and never with a condition, which a database check also refuses.
- With no default flow, the case stops in `ERROR` and its timeline names the gateway that found no path
  (`SIN_CAMINO`). An administrator corrects the variables with `PATCH /api/v1/casos/{id}/variables` and retries
  with `POST /api/v1/casos/{id}/reintentar`.
- What is drawn inside another participant is not run.

## Consequences

- The rule "every flow leaving an exclusive or inclusive gateway has a condition" became "every flow has a condition
  or is the default flow". It is checked when saving (R-35, R-36) and by the diagnosis (`E-07`).
- A gateway that decides without a default flow is a warning, not an error (`A-05`, `A-14`).
- The subset is what the project needs: there are no timer or error events.

## Verification

- `MotorDeProcesosTest`: one test per row of the table.
- `GrafoDeVersionTest`: the order of the flows, and the reachability an inclusive join relies on, cycles included.
- `ArcoServiceTest` (R-35, R-36) and `ArcoRepositoryTest`: the database refuses a default flow with a condition.
- The nightly *Mutation Testing* workflow mutates the services, the engine among them, and fails when fewer than
  75 % of the mutations are caught.

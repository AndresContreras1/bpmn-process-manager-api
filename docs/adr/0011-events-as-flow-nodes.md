[← Decision records](README.md)

# ADR-0011: Events are the third subtype of `NodoFlujo`

- **Status:** Accepted
- **Decided in:** plan v3 (D11)

## Context

HU-04, HU-07 and HU-11 name events, HU-27 needs a message start event and an intermediate one, and the engine needs
to know where a case starts and where it ends. Flow nodes were activities and gateways in one table.

## Decision

`Evento extends NodoFlujo`, with discriminator `EVENTO` and a `tipoEvento`:

| Type | What it is |
|---|---|
| `INICIO` | Starts the process without waiting for anything |
| `FIN` | Ends a path |
| `MENSAJE_INICIO` | Starts the process when a message arrives |
| `MENSAJE_INTERMEDIO` | Waits for a message in the middle of the flow |
| `MENSAJE_FIN` | Sends a message and ends its path |

Hard rules (R-31, R-32): no flow arrives at a start event, none leaves an end event, and an event with flows cannot
become a type they forbid. An event lives in a lane like any other node. There are no timer or error events.

## Consequences

- `Arco` did not change: it already pointed to any `NodoFlujo`.
- The whole diagram gained the list `eventos`, and events have their own endpoints under
  `/api/v1/lanes/{laneId}/eventos` and `/api/v1/eventos/{id}`.
- The web viewer draws them as BPMN circles: thin for a start, double for an intermediate message event, thick for
  an end, with an envelope on the three message events, filled when the event sends.
- With single-table inheritance, `Evento` declares its own soft delete, and the database requires the type of each
  event (`ck_nodos_flujo_evento_con_tipo`).

## Verification

- `HerenciaJpaTest.subtipos_extienden_NodoFlujo`: the `Actividad`, `Gateway` and `Evento` entities extend
  `NodoFlujo`; `subtipos_declaran_su_borrado` and `nodoFlujo_usa_single_table` hold with the third one.
- `EventoServiceTest` and `ConsistenciaBpmnIntegracionTest`: R-31 and R-32.
- `EventoRepositoryTest`: the database requires the type of an event.
- `DiagnosticoServiceTest`: `E-09` fires for a start event with incoming flows or an end event with outgoing ones.

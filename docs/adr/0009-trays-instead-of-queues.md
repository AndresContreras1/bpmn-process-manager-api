[← Decision records](README.md)

# ADR-0009: Trays instead of queues

- **Status:** Accepted
- **Decided in:** plan v3 (D9)

## Context

An order arrives as a message, a payment is requested, a parcel is confirmed. A queue would move those messages and
keep no history a store can read, and what explains an order that is waiting is exactly what it sent and what it is
still waiting for.

## Decision

Two tables instead of a broker.

- `mensajes_salientes` is what the store sent: the message of the version, the destination pool and its
  `integracion`, how it travels, the key the answer will come back with, the body, the state (`PENDIENTE`,
  `ENTREGADO`, `FALLIDO`), the tick it is due and, when it failed, why.
- `mensajes_entrantes` is what arrived: its name, key and body, where it came from (`MANUAL` or a simulated partner),
  the case it reached, an optional `claveExterna` unique within the store, and the result of the correlation:
  `ENTREGADO_A_CASO`, `CASO_NUEVO`, `EN_ESPERA`, `PROGRAMADO` (an answer a partner left for a later tick) or
  `DESCARTADO`.
- A message can also arrive by hand: `POST /api/v1/procesos/{procesoId}/mensajes-entrantes` stands in for a
  partner's webhook or a customer's order.

## Consequences

- What happened is in two lists per process, `GET /api/v1/procesos/{procesoId}/bandeja-salida` and
  `GET /api/v1/procesos/{procesoId}/bandeja-entrada`, and in the case's own view, `GET /api/v1/casos/{casoId}/mensajes`.
- Nothing is lost: a message that matches no case is kept as `DESCARTADO`. A message without a key is never delivered
  to a case because its name matches.
- `siFalla` (HU-26) stops being documentation: when a send fails, `CONTINUAR` goes on, `MANEJAR_ERROR` activates the
  activity that handles it, and `FINALIZAR` ends the case `FALLIDO`.

## Verification

- `MensajeriaIntegracionTest`: the endings of the correlation on a real process, a message without a key, a closed
  case, and the same message sent twice.
- `MensajeriaRepositoryTest`: the queries of both trays, and an external key unique within a store and free across
  stores.
- `MotorDeProcesosTest` and `SimulacionIntegracionTest`: what each `siFalla` does when a send does not arrive.

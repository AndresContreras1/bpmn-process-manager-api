[← Decision records](README.md)

# ADR-0012: Messages are anchored to nodes

- **Status:** Accepted
- **Decided in:** plan v3 (D12)

## Context

A message flow joined two pools. HU-25, HU-26 and HU-27 ask from which point of the flow a message is sent and at
which point it is received, how it travels, what happens when it fails and what it carries.

## Decision

`Mensaje` gained these fields:

- `nodoOrigen`, the node that sends it: a `MENSAJE_FIN` event or an activity of type `ENVIO` or `SERVICIO`;
  `nodoDestino`, the node that waits for it: a `MENSAJE_INICIO` or `MENSAJE_INTERMEDIO` event, or a `RECEPCION`
  activity.
- `tipoDestino` (`CORREO`, `SERVICIO_WEB`, `COLA`) and `siFalla` (`CONTINUAR` by default, `MANEJAR_ERROR` with
  `nodoManejoError`, or `FINALIZAR`).
- `origenExterno`, `usoDeLosDatos`, and `campos`: a list of `{nombre, tipo}` with `TEXTO`, `NUMERO`, `FECHA` or
  `BOOLEANO`, stored as JSON in the message's row.
- `variable`, the name the body takes among the case variables; by default, the message name in camelCase.
- `respuestaEsperada`, the message of the same process that answers this one.

Rules when saving: an anchored node is in the pool of its side and can send or receive (R-37); a black-box pool
anchors nothing (R-38); the activity that handles a failure is in the sending pool, and only `MANEJAR_ERROR` names
one (R-39); the answer comes back from the pool that received the message (R-40).

## Consequences

- Every new field of `MensajeRequest` is optional, so the earlier contract keeps working.
- Saving accepts a message without anchors. The diagnosis makes a missing anchor an error where the engine needs
  one: a node that exists to exchange a message and has none (`E-10`, `E-11`), and a message between two pools
  modeled inside with an end left loose (`E-13`).
- The engine sends from the anchored node and waits at the anchored node. The web viewer draws the dashed line from
  the node instead of from the edge of the pool.

## Verification

- `MensajeServiceTest` and `ConsistenciaBpmnIntegracionTest`: R-37 to R-40.
- `MensajeRepositoryTest`: the anchors, the answer and the fields stored as JSON.
- `DiagnosticoServiceTest`: `E-10` to `E-13`.
- How the viewer draws an anchored message is not covered by an automated test.

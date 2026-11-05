[← Decision records](README.md)

# ADR-0007: Partners are adapters behind a port, and today all of them are simulated

- **Status:** Accepted
- **Decided in:** plan v3 (D7)

## Context

Payments, shipping and notifications are integrations the diagram already documented as black-box pools. Real ones
would need accounts, keys and network calls, and every test of an order would depend on something outside answering.

## Decision

A pool declares the kind of partner behind it, `integracion`: `NINGUNA` (the default), `CLIENTE`, `PAGOS`,
`TRANSPORTE` or `NOTIFICACIONES`. When the clock delivers an outgoing message, `ejecucion` hands it to the
`SocioSimulado` of the destination pool's kind, and the partner answers with the messages the same diagram declares
from that pool: the `respuestaEsperada` of what it received, and later the messages of that pool marked
`origenExterno`.

- `integracion.simulado` has five: the payment gateway, the carrier, the notifier, the customer, which also makes
  up batches of orders through `GeneradorDePedidos`, and an echo for a participant with no partner of its own.
- What they decide is deterministic per store. Rates are decided by a hash of the store's seed, the case and the
  message (`SemillaDeterminista`), not by `Random`. The payment rejection rule is written in the condition language
  (`total > 5000`) and checked when it is saved. Latencies are counted in ticks.
- A store sets all of it in the `simulacion` block of `PUT /api/v1/empresas/actual/configuracion`.

## Consequences

- The diagram is the contract of the integration: a partner answers what the diagram says it answers.
- A real payment provider or carrier would be one more implementation of the port, with no change to `ejecucion`.
- The same store, seed and steps give the same rejections, lost parcels and amounts, so a demo can be shown twice,
  and a test forces an outcome with a rate of `0` or `100`.
- No class names a simulated partner: they are package-private Spring components, and `SociosSimulados` receives
  them as a list of the port.

## Verification

- `EmpaquetadoTest.los_simulados_no_hacen_red`: `integracion` does not depend on `java.net`,
  `org.springframework.web.client` or `org.springframework.web.reactive`, so no `HttpClient` and no `RestClient`.
  `los_simulados_no_miran_dentro_del_motor` keeps it out of the engine. Both messages cite D7.
- One suite per partner (`PasarelaSimuladaTest`, `TransportistaSimuladoTest`, `NotificadorSimuladoTest`,
  `ClienteSimuladoTest`, `EcoSimuladoTest`) and `SemillaDeterministaTest`.
- `VeintePedidosTest` and `DemoDePuntaAPuntaTest`: whole orders through the partners, approved and rejected.

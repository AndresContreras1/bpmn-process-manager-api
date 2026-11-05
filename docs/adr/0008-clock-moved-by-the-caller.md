[← Decision records](README.md)

# ADR-0008: The clock is moved by whoever tests

- **Status:** Accepted
- **Decided in:** plan v3 (D8)

## Context

A message is due at some moment, and a partner answers some time later. A `@Scheduled` job delivering messages every
second would make every test of execution depend on timing, and a demo would never show the same thing twice.

## Decision

Each store has its own clock, `configuracion_tienda.reloj`: a counter of ticks that starts at zero and only goes up.
`POST /api/v1/simulacion/tick` with `{"ticks": n}`, from 1 to 100, moves it. Moving it retries the incoming messages
that were waiting, hands every outgoing message now due to its partner, correlates the answers and moves the cases
they reach, one message at a time, each in its own transaction with its case locked
([ADR-0003](0003-lock-the-case-row.md)).

A store can set `modoSimulacion` to `AUTOMATICO` instead of the default `MANUAL`. Then `SimulacionConfig`, a
`@Scheduled` job every `SIMULACION_TICK` (30 seconds by default), calls the same service one tick at a time for each
of those stores. The job is `@Profile("!test")`, so no test has it running behind it.

## Consequences

- Every test of execution is a script: open a case, complete a task, tick, check.
- A message is due at least one tick after it is sent, so an order waiting for the payment gateway can be seen.
- The engine does not read the clock: it receives the moment it works in, and a case, its steps and its timeline
  carry the store's tick. The dashboard counts time in ticks.
- One store's clock never moves another's.

## Verification

- `EmpaquetadoTest.lo_que_corre_solo_vive_en_config`: every `@Scheduled` method is declared in `config`.
- `PerfilesTest`: `dev` registers `SimulacionConfig` and `LimpiezaConfig`, and `test` registers neither.
- `SimulacionIntegracionTest` and `RelojDeLaTiendaTest`: a message sent in one tick arrives in a later one, the
  partner's latency decides which, the tick stamps what a case does, and one store's tick touches no other store.
- `AutorizacionPorRolTest`: moving the clock is the administrator's.

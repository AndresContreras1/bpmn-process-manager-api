[← Decision records](README.md)

# ADR-0003: A case advances with its row locked

- **Status:** Accepted
- **Decided in:** plan v3 (D3)

## Context

Completing a task, receiving a message and moving the clock can touch the same case at the same moment. Optimistic
locking with `@Version` is not enough: each live token is a different row, so two operations could each change a
different row and both commit.

## Decision

Every operation that moves a case starts with `CasoRepository.bloquear(id, empresaId)`, a `PESSIMISTIC_WRITE` lock
on the case's row like the one `EmpresaRepository.bloquear` takes to protect the last administrator, and ends in the
same transaction. Completing a task, cancelling a case, correcting its variables, retrying it, delivering an outgoing
message and handing an incoming one to its case all start there.

- Whoever completes a task finds its case with `ActividadCasoRepository.casoDe` and locks it **before** reading the
  task, so the second caller reads the task after the first one committed.
- Completing a task that is no longer waiting answers `409` ("La tarea ya fue completada.").
- An incoming message with a `claveExterna` the store already received is not processed again: the API answers the
  first result, marked `repetido: true`.

## Consequences

- The engine, `MotorDeProcesos`, keeps no state: it receives the locked case and works inside its transaction.
- The lock is one row: two stores never wait for each other, and neither do two cases of the same store.
- A tick delivers each due message in its own transaction with its case locked, so twenty orders move one after
  another, and one that fails does not leave the others half done.

## Verification

- `ConcurrenciaDeCasosTest`: two threads complete the same task, in the way `AdministradoresIntegracionTest` tests
  two administrators. The second waits on the lock, then fails with the business-rule error the API answers as
  `409`, and the case goes through each node once.
- `CasoRepositoryTest`: `bloquear` returns the case of the store and nothing of another one.
- `MensajeriaIntegracionTest` and `DemoDePuntaAPuntaTest`: the same message sent twice is processed once.

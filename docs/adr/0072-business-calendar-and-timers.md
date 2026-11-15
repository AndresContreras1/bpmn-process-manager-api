[← Decision records](README.md)

# ADR-0072: Business calendar, timers and the two modes of a case

- **Status:** Accepted
- **Decided in:** plan v5 (PR 68), decisions D8, D34 and D35

## Context

Until now every case ran on the store's tick clock (D8): a counter that whoever tests moves by hand, with simulated
partners answering after so many ticks. That keeps every test deterministic, but a real order waits real hours, and a
deadline like "two business days" depends on the store's working hours and on its country's public holidays. Timer
events were already in the model since PR 52–55 (start, intermediate and boundary, with their ISO 8601 expression),
but the diagnosis did not let an executable process publish one (`E-18`), because nothing fired them.

## Decision

- **Two modes per case.** `PRODUCCION` counts time with the wall clock (an injected `java.time.Clock`) and the store's
  business calendar. `PRUEBA` keeps the ticks and the simulated partners of plan v3 whole. The column `casos.modo`
  says which.
- **A case opened the way cases opened before is a `PRUEBA` case.** `POST /procesos/{id}/casos` keeps its contract;
  `{"modo": "PRODUCCION"}` asks for the other mode. A case opened by an incoming message is `PRUEBA` too, so the
  simulated orders and every existing engine test behave exactly as before. A timer start opens `PRODUCCION` cases.
- **Production does not talk to simulated partners.** The tick only delivers the outgoing messages of `PRUEBA` cases.
  Those of a `PRODUCCION` case wait in the outbox for an integration to read them (the real connectors of PR 77–78
  will deliver them), and answers come in through the incoming-message endpoint.
- **A throwaway test version to try a draft.** `POST /procesos/{id}/pruebas` freezes today's draft as a version of
  type `PRUEBA`: number 0, born `RETIRADA`, excluded from every version query, so it is never listed, never read by
  number and never the version in force. A `PRUEBA` case runs on it, from its plain start, its timer start (without
  waiting for it) or its message start. The draft has to pass the same diagnosis as publishing, but not "has changes".
  The nightly purge deletes the test versions older than `PRUEBAS_RETENCION` (one day), with their cases: `gestion`
  says which versions are old and deletes them last, `ejecucion` deletes what their cases left, and every one of those
  deletes checks again that the version is of type `PRUEBA`, so a published version and its cases survive even a bug
  in the query that picks the old ones. The age is counted with the same injected clock that dates the test version.
- **Test runs are listed, but they are not orders.** A test-run case shows in `GET /casos` and its tasks reach the
  role trays on purpose, so whoever tests can walk each path. An incoming message never correlates with one (the
  lookup by reference only sees cases of published versions: a message from outside belongs to the real orders, and
  the test run runs another graph), and the dashboard and the open-cases gauge leave them out.
- **A business calendar per store** (`calendarios`, `festivos`): country, IANA time zone, working hours and working
  days. Until an administrator saves one, the store uses its country's: the country follows its language (`es-CO`,
  `en-US`, `fr-FR`, `en-AU`), the zone its settings, Monday to Friday from 08:00 to 17:00. Public holidays are
  computed, never loaded from a table: Colombia (Ley 51 de 1983, with the holidays moved to Monday and those that
  follow Easter), the United States (5 U.S.C. 6103, with the observed day), France (Code du travail L3133-1) and
  Australia (the national ones, with the additional day when one falls on a weekend). Easter comes from the anonymous
  Gregorian algorithm. A store adds, renames or removes a day (`festivos` keeps only its changes), which is how the
  state holidays of Australia and the United States come in.
- **Business time.** It runs only inside the working hours of a working day that is not a holiday, in the store's
  zone: a day whose hours cross a change of clock lasts what it really lasts. One business day is as many business
  hours as the working day has: two business days from Friday 15:00 are Tuesday 15:00.
- **One grammar for timer expressions** (`common.tiempo`), shared by the diagnosis and the engine: ISO 8601
  durations, dates and repeating intervals (`R3/PT1H`, `R/2026-01-01T08:00/P1D`, `R2/PT1H/2026-01-01T08:00Z`) and
  six-field cron expressions for cycles. A new timer type, `DURACION_HABIL`, counts days and hours of work; in BPMN it
  is a `timeDuration` with `bpm:habil="true"`, so another tool reads a calendar duration.
- **Caps on what a timer can say**, in the same grammar, so whatever the diagnosis accepts can always be armed and no
  published process can flood a store: a duration up to a hundred years (months counted as 31 days), a business
  duration up to 366 working days or 366 days of working hours, dates from 1900 to 9999, and a cycle no more often
  than once a minute (an ISO period of at least a minute, a cron expression whose seconds field is a fixed number, or
  a macro). Arming still catches a count that falls outside the dates, such as a business duration on a calendar
  whose working day lasts minutes: the timer is not armed, the case's timeline says why, and neither opening the case
  nor publishing fails.
- **Timers are rows** (`temporizadores`). The engine arms a wait's timer when a token reaches it, and the boundary
  timers of an activity when it starts waiting (a user task, a receive). Publishing arms the timer starts of the
  version in force, always in `PRODUCCION`, and cancels those of the previous one; retiring the version in force arms
  those of the one that is in force again. After each pass of the engine, the timers whose step no longer waits are
  cancelled, and all of them when the case closes. A case in error keeps its timers: they fire after it is rescued.
- **When a timer is due.** In `PRODUCCION`, from the wall clock and the store's calendar (calendar durations in the
  store's zone, so `P1D` is the same wall time the next day). In `PRUEBA` a tick is one hour: `PT2H` is two ticks, two
  business days are two working days of ticks, and what has no length, a date or a cron, is due on the next tick.
  Never in the same tick it is armed, like a message.
- **Firing exactly once** (D34). A scheduler in `config` runs every `TEMPORIZADORES_REPASO` (five seconds) under the
  ShedLock lock that already exists, finds the due `PRODUCCION` timers of every store without locking, and fires each
  one in its own transaction with its store's scope: first the case is locked, as every operation that moves it does
  (D3), then the timer row is taken with `FOR UPDATE SKIP LOCKED` asking that it is still armed and due. Two instances
  that find the same timer fire it once: the second waits for the case and then finds it fired. A timer start, which
  has no case, is fired by whoever takes it first. A timer that was due while the API was down fires once when it
  comes back; a cycle then continues with its next turn and does not catch up the ones it missed. The tick fires the
  due `PRUEBA` timers of its store after delivering its messages. A timer that fails is postponed and kept with its
  error, and after three failures it stays `FALLIDO`. What it keeps is a fixed reason with the exception's class, since
  any role of the store reads it; the message goes to the server log.
- **What fires.** A wait lets its token go on. A boundary timer that interrupts takes its activity out (its task
  leaves the tray) and the case follows the boundary; one that does not interrupt opens another path and, if it is a
  cycle, fires again while the activity waits. A timer start opens a case on its version if that version is still in
  force. This boundary timer is the general one plan PR 71 builds deadlines on.
- **`E-18` changes meaning.** Timers run, so it no longer blocks them; it now flags a cycle on a timer that can only
  fire once (an intermediate one, or a boundary one that interrupts).

## Consequences

- An executable process can publish its timers, and its cases wait for them.
- `TEMPORIZADOR_INTERMEDIO` is longer than the 20 characters a case step kept for its subtype; V58 widens that column
  to 30, the same as the events of the model.
- A test version and its case stay in the database for a day; the version never reaches any version list, and its
  case shows version 0.
- In `PRUEBA` the hours of a timer are approximate by design: what matters is the path the case takes, and the order
  in which its timers fire.
- The holidays of a year are computed each time a calendar is read; it is a few dozen dates.
- The web does not edit the calendar yet: it is the API's for now. The web modeler's properties panel does not offer
  `DURACION_HABIL` either: a business duration is set through the API or an imported BPMN file (`bpm:habil`) until a
  later web PR adds it.
- `GET /procesos/{id}/temporizadores` is paged, because every publish adds timer starts; `GET
  /organizacion/calendario/ediciones` is not, because a store changes at most 500 days.

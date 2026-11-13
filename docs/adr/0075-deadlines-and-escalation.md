[← Decision records](README.md)

# ADR-0075: Deadlines, notices and escalation

- **Status:** Accepted
- **Decided in:** plan v5 (PR 71); builds on [ADR-0072](0072-business-calendar-and-timers.md) (the calendar and the
  boundary timer), [ADR-0074](0074-assignment-and-inbox.md) (who holds a task) and
  [ADR-0067](0067-comments-mentions-and-notifications.md) (the notification center)

## Context

A task waited in its tray for as long as it took, and nobody knew it was late until a customer complained. A store
needs to say how long a task, or a whole case, may take, in hours or in business days of its own calendar; to see
which ones are on time, due soon or overdue; to tell the people concerned before and when the deadline passes; to
escalate a task that waits too long; and to measure, afterwards, how often deadlines were kept and by whom.

## Decision

- **A deadline on the node, frozen in the version.** `PUT /actividades/{id}/plazo` saves `{ cantidad, unidad }` on a
  user task, and `PUT /eventos/{id}/plazo` on any start event, as the deadline of the whole case; without both values
  the node has none. `unidad` is `HORAS` (wall-clock hours, 1 to 8,784) or `DIAS_HABILES` (business days of the store's
  calendar, 1 to 366), the caps of a timer. The deadline lives in `nodos_flujo.plazo_cantidad` and `plazo_unidad`,
  with check constraints that keep the pair together, within its cap and only on a user activity or a start event; the
  activity and event services refuse a type change that would leave it on another node (`409`), as they do with forms
  and assignments. It travels in the diagram, so publishing freezes it, and the version fingerprint only changes when
  a node has one.
- **When a deadline is due.** When a case reaches a user task, or opens through a start event, the engine counts the
  deadline from that moment. Hours are added to the instant, so a clock change does not stretch or shrink them;
  business days are working days of the PR 68 calendar (`CalendarioLaboral.sumarHoras`): two business days from a
  Friday at three are the next Tuesday at three, skipping the weekend and any holiday. The task, or the case, keeps
  `vence_en` and `aviso_en` (UTC) in production, or `vence_tick` and `aviso_tick` in test mode, where a tick is an hour
  like for a timer and a business day is as many ticks as the working day has hours. `aviso_*` is where the last quarter
  of the deadline starts. The deadline goes into the timeline (`"Review refund" vence el 2026-07-14 15:00
  (America/Bogota)`). A calendar that cannot count it arms none, and the timeline says so; the case goes on.
- **Three states that only move forward.** `estado_plazo` is `A_TIEMPO`, `POR_VENCER` (from `aviso_*`) or `VENCIDA`
  (from `vence_*`). A sweep moves them while the task waits, or the case is open or in error: in production a scheduler
  under ShedLock every `PLAZOS_REPASO` (one minute), across stores, each one in its store's scope and its own
  transaction; in test mode the store's tick, after its messages and timers. When a task is completed, or a case
  finishes, its state is set from the clock at that moment, so one completed after its deadline stays `VENCIDA` even if
  the sweep had not passed yet; a cancelled or failed case is not measured.
- **Each notice exactly once.** The sweep reads the row, computes the state the clock gives it, and writes it with a
  conditional `UPDATE … SET estado_plazo = :nuevo WHERE id = :id AND estado = 'EN_ESPERA' AND estado_plazo =
  :anterior`. Two sweeps that find the same row both write, but the database makes the second wait and then matches no
  row, so only the first one writes the timeline line and publishes the notice, in the same transaction. The update
  does not bump the optimistic version: it is nobody's change, and a person completing or reassigning the task must not
  get a `409` for it. The entities are mapped with `@DynamicUpdate`, so a save of a task or a case writes only the
  columns that changed and never puts back a state it read before the sweep moved it. A task that jumps from on time to
  overdue (the API was down, or the deadline is shorter than a pass) only gets the overdue notice.
- **Through the notification center.** Three new `TipoDeNotificacion` values, e-mailed at once by default and in
  everybody's preferences: `PLAZO_POR_VENCER`, `PLAZO_VENCIDO` and `TAREA_ESCALADA`. Their `referenciaId` is the case
  (the comment types point to a comment), so their e-mail links to `/casos/{id}` with a "See the case" button; their
  words are in Spanish, English and French. A task's notice goes to whoever holds it, or, while it waits in its role
  tray, to the members of that role, and to the process owner (`procesos.dueno_id`); a case's, to whoever opened it and
  the owner. The system is the author, so nobody is left out for having caused it, and the notification center still
  drops inactive people.
- **Escalation is the boundary timer of PR 68.** An interrupting boundary timer already takes the task out and the
  case follows the boundary's path. A non-interrupting one, which until now only opened its path, can also escalate:
  `PUT /eventos/{id}/escalamiento` saves `AVISAR` (notify) or `REASIGNAR` (hand the task over) and the supervisor
  (`usuarioId`, an active administrator or editor), or the process owner when it names nobody. It lives in
  `nodos_flujo.escalamiento` and `escalar_a`, only on a non-interrupting boundary timer (a check constraint and the
  event service hold it), and travels in BPMN. When the timer fires:
  - `AVISAR` notifies the holder and the supervisor (`TAREA_ESCALADA`).
  - `REASIGNAR` hands the task to the supervisor, ending an open delegation as `ANULADA`, writes the timeline line and
    the audit event `TAREA_ESCALADA` (the system as actor, with the case and both user ids), and notifies both. If the
    supervisor cannot hold tasks, already holds it, or the boundary hangs from something that is not a user task, it
    notifies instead and the timeline says why.
  - Without a supervisor and without an owner the timeline says there was nobody to escalate to.
  A cycle escalates every time it fires. The boundary's own expression says when (`PT6H`, a business duration, a
  cycle), independent of the deadline.
- **Compliance in the dashboard.** `GET /procesos/{id}/tablero` and `GET /empresas/actual/tablero` gain `cumplimiento`:
  tasks on time and late in total, by process, by activity and by person, and whole cases on time and late. A task
  counts once its outcome is known: on time if completed before its deadline, late if completed after it or still
  waiting past it. A task still on time and waiting, an omitted one, one without a deadline or a test run does not
  count. By person is whoever completed it, or holds it while it waits overdue. Four grouped queries, so the
  dashboard costs ten queries however many cases there are.
- **Inboxes by due date.** Every inbox of PR 70 is ordered by `vence_en`, then by `vence_tick` (test runs), then the
  tasks without a deadline, oldest first. Tasks and cases answer `venceEn`, `venceTick` and `estadoPlazo`.
- **In BPMN.** `bpm:plazo` (`cantidad`, `unidad`) goes in the `extensionElements` of a `userTask` or a `startEvent`,
  and `bpm:escalamiento` (`accion`, `usuario`) in those of a non-interrupting timer `boundaryEvent`, declared in
  `bpm.xsd` and in the web's `bpm:` package so saving from bpmn-js keeps them. The importer leaves out, with a note, a
  deadline that cannot be counted or sits on another node, and an escalation with an unknown action or on another
  event.

## Consequences

- The deadline is not a timer: a deadline changes a state and notifies, while a timer moves the case. A store that
  wants the case to act when a task is late draws a boundary timer, and gives it the same time if it wants both at
  once. Keeping them apart keeps the temporizadores table for what the engine runs, and the deadline on the task where
  the inbox and the dashboard read it.
- The process owner, set in the PR 59 filing, becomes who hears about late work by default; a process without an owner
  only tells the people of the task.
- The web shows the new timeline lines and keeps the new BPMN elements when saving; editing deadlines and escalation in
  the modeler and showing due dates in the tray belong to the work portal and the form designer screens (plan F23 and
  F24). The API is complete.
- `NotificacionServiceImpl.listar` no longer asks an empty map for a null author or process: notices from the system
  have neither.

## Verification

- `CuandoVenceElPlazoTest`: deadlines counted by hand across a weekend and the Colombian Epiphany Monday, the New York
  spring clock change in hours, a business day across the Paris change, Good Friday, Easter Monday and the Sydney clock
  change, and the ticks of a test case; `VencimientoTest`: each state at its exact edges and that a state never goes
  back.
- `PlazosTest`, `RevisionDePlazosTest`, `AvisosDePlazoTest`, `EscalamientosTest`, `PlazoServiceImplTest`,
  `TableroServiceImplTest`, `PlazoDelNodoServiceImplTest`, `CorreosDeNotificacionesTest` and
  `NotificacionServiceImplTest`: each rule with mocks.
- `MotorConPlazosTest` (PostgreSQL): the engine arms each deadline, the sweep moves them with the real queries and the
  conditional update and notifies once, a stale save does not put a state back, a test case moves with ticks, the
  inbox order, both escalation kinds and the interrupting boundary, and the compliance queries against tasks and cases
  counted by hand.
- `PlazoBpmnTest` (schema, round trip, notes), `AplicacionDelModeloTest`, `HuellaDelDiagramaTest`,
  `GrafoDeVersionTest`, `ActividadServiceTest` and `EventoServiceTest`.
- `PlazosIntegracionTest`: saving deadlines and escalation by HTTP (roles, `400`, `409`, `404` for another store),
  a production case from start to end with a clock moved by hand (due soon, escalated to the supervisor, overdue,
  each notice once in the notification table, completed late, counted late in the dashboard), the inbox order with a
  test run, and row-level security on the deadlines and the notices.
- `AutorizacionPorRolTest` and `ReglaDeRecursoTest`: the three new routes.

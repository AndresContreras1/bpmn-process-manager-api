[← Decision records](README.md)

# ADR-0074: Assignment, inboxes and who completes a task

- **Status:** Accepted
- **Decided in:** plan v5 (PR 70); amends the completion rule of [ADR-0013](0013-trays-by-process-role.md)

## Context

Until now a user task was born in the tray of its lane's process role, and any administrator or editor of the store
could complete it. Taking a task (`POST /tareas/{id}/asignar`) was a note for the team: the whole role kept seeing it
and anybody could still complete it. A real store needs a task to reach a given person (the one who opened the case,
the approver chosen in a form, a fixed specialist), to know who is working on what, to hand work over with a reason,
and to make sure two people do not both work on the same task. It also needs to answer "who did this?" without
guessing.

## Decision

- **An assignment rule per user task, frozen in the version.** `PUT /actividades/{id}/asignacion` saves one of four
  rules on a user activity of the draft (`MODELAR` on the element, with the node's `version`):
  - `ROL`, the default and what every task did before: the task waits in the tray of its lane's process role. It is
    stored as no rule at all, so it can be set on any activity.
  - `PERSONA`: a fixed person, an active administrator or editor of the store (`400` on `usuarioId` otherwise).
  - `INICIADOR`: whoever opened the case by hand (`casos.iniciado_por`, new in V60).
  - `CAMPO`: the person a case variable names, written like a gateway condition names it: `aprobador` for a field of
    the start form, `tarea.revisarPedido.aprobador` for one of an earlier task. It is meant for the `PERSONA` field of
    PR 69, which stores a user id.

  The rule lives in three columns of `nodos_flujo` (`asignacion`, `asignacion_usuario_id`, `asignacion_campo`) with
  check constraints that keep each kind with only what it needs and only on a user activity; the activity service also
  refuses to change the type of an activity that carries one (`409`), as it does with forms. It travels in the diagram,
  so publishing freezes it in the version: a case assigns its tasks with the rule of the version it runs, and the
  version fingerprint only changes when an activity has a rule.
- **Resolved when the task is created, never blocking.** When a case reaches a user task, the engine applies the frozen
  rule. If the person cannot be told (a case opened by a message or a timer has no initiator; the variable is missing
  or is not an integer id) or cannot work (deactivated, read-only, or not a user of this store, as a fixed id imported
  from another store's BPMN would be), the task waits in the role tray, as before, and the `TAREA_CREADA` line of the
  case timeline says why. A case never stops because somebody left the store.
- **Only whoever holds a task completes it.** `actividades_caso.asignado_a` is no longer a note: it is the holder.
  - A task nobody holds is taken by completing it, by whoever could claim it; anyone else gets `403`. The claim is
    recorded like any other: a `TAREA_RECLAMADA` timeline line ("taken by completing it") and audit event.
  - A task another person holds answers `409`, unless the caller is an administrator **and** the store turned on the
    policy `administradorCompletaPorOtro` (`configuracion_tienda.administrador_completa_por_otro`, off by default,
    edited with the rest of the store settings). Then the task is completed on the holder's behalf: it keeps its
    holder, the timeline line says an administrator completed it for that person, and the audit trail records
    `TAREA_COMPLETADA_POR_OTRO` with both ids.
- **Who may take a task: the role's members, and administrators.** Membership (D13) now decides who can claim a task
  nobody holds: a member of the task's process role, or an administrator, who distributes the work. A store that never
  sets memberships keeps working through its administrators, and an administrator can always hand a task to anyone.
  This is the part of ADR-0013 that changes: a membership used to filter and grant nothing.
- **Four actions while a task waits in an open case, each in the timeline and the audit trail.** A task that no
  longer waits, or whose case is in error or closed, answers `409`.
  - `POST /tareas/{id}/reclamar`: a candidate takes a task nobody holds.
  - `POST /tareas/{id}/soltar` (optional reason): the holder, or an administrator, lets it go. It goes back to the role
    tray, or, when the holder is a delegate, back to whoever delegated it, as long as that person can still hold tasks
    (active and not read-only). If not, it goes to the role tray and the timeline says why.
  - `POST /tareas/{id}/reasignar` (person and reason): the holder, or an administrator, hands it to another person who
    can complete tasks. An administrator can also give a task nobody holds. A delegated task is reassigned by whoever
    delegated it, or by an administrator, never by the delegate, who completes it or gives it back: otherwise a
    delegate could keep it from coming back by passing it on. An open delegation ends as `ANULADA`.
  - `POST /tareas/{id}/delegar` (person and reason): only the holder hands it over for a while. The task records who
    delegated it (`actividades_caso.delegada_por`) and a row of `delegaciones` keeps the reason and how it ended:
    `DEVUELTA` when the delegate gives it back, `COMPLETADA` when it is completed while delegated (by the delegate,
    or by an administrator on their behalf), `ANULADA` when someone reassigns it or releases it to the role, or when
    the case leaves the task behind (cancelled, failed, or an interrupting boundary timer). A delegated task is not
    delegated again; a partial unique index keeps one open delegation per task.

  Timeline types `TAREA_ASIGNADA`, `TAREA_RECLAMADA`, `TAREA_LIBERADA`, `TAREA_REASIGNADA` and `TAREA_DELEGADA` join
  `eventos_caso`; the reason goes to the timeline line and the delegation row, and the audit trail keeps the case and
  the two user ids, not the free text. Lines name people by id: the timeline is not rewritten when a user is
  anonymized (PR 35), so it carries no names. The reason is free text and is not rewritten either; the API asks for
  the why, not for names, and `docs/security.md` says so.
- **Race safety: the case lock and an atomic update.** Every action, claiming included, locks the case first (D3),
  as completing, cancelling and a firing timer do, so a claim never lands between a cancellation's read and its write.
  Two people claiming at once queue on that lock; the second then reads a task somebody holds and gets `409`. The
  claim itself is still a single `UPDATE … SET asignado_a = :yo, version = version + 1 WHERE id = :id AND estado =
  'EN_ESPERA' AND asignado_a IS NULL`, so even a writer that skipped the lock could not make two people win: the row
  no longer matches and the claim answers `409`. Every other action saves the task with its `@Version`.
- **Inboxes.** `GET /tareas?bandeja=` picks one: `TODAS` (the store), `MIAS` (what the caller holds), `DE_MIS_ROLES`
  (the tasks of the caller's roles, whoever holds them; `mias=true` keeps meaning this) and `SIN_ASIGNAR` (what nobody
  holds), with the filters by role, process and state as before. They are ordered oldest first. Plan PR 71 adds due
  dates and orders them by due date then; the plan puts `vence_en` in that PR, so no column is added here.
  `GET /tareas/carga` answers the workload: how many waiting tasks each person holds, the busiest first, and how many
  nobody holds; test runs of a draft do not count.
- **In BPMN as `bpm:asignacion`.** The writer puts the rule in the user task's `extensionElements` with `tipo` and
  `usuario` or `campo`, declared in `bpm.xsd`; the role tray writes nothing. The reader brings it back; a rule on
  anything but a user task, of a kind that does not exist, a fixed person with no id or a variable that is missing or
  longer than its column, is left out with a note and the task waits in its role tray. The web declares the type in its
  `bpm:` package, so saving from bpmn-js keeps it.
- **Checked again when publishing.** A rule that arrives inside a BPMN file did not go through the API, so the diagnosis
  reports `E-23` for a rule that cannot work (on an activity that is not a user task, a fixed person with no id, a
  variable that is not a path) and the advisory `A-17` for a variable that no `PERSONA` field of a form in the diagram
  writes. Whether the fixed person is still an active user is not a diagnosis question: the diagnosis reads only the
  diagram, and the engine falls back to the role tray anyway.

## Consequences

- `POST /tareas/{id}/asignar` is gone; the web's "Take" and "Release" buttons call `reclamar` and `soltar`, and
  "Complete" only shows on a task the viewer holds or nobody holds; the case timeline names the new events. The web
  inboxes, the workload, reassigning and delegating are the work portal (plan F24); the panel for the rule goes in the
  modeler beside the form designer (plan F23). The API is complete.
- An editor who belongs to no process role can no longer complete a task nobody holds: an administrator assigns it, or
  adds the editor to the role. Existing tests and demos that complete tasks as administrators keep working, because an
  administrator claims by completing.
- A person in a `CAMPO` variable is checked when the task is created; the `PERSONA` field of PR 69 already checks that
  it names an active user when the form is answered.
- Notices to the person who gets a task, and escalation when it is late, are plan PR 71.

## Verification

- `AsignacionDeTareaServiceTest`: each action, who may do it (the delegate who may not reassign, the delegator who
  may), what it leaves in the task, the delegation, the timeline and the audit trail, the case lock before every read,
  the closed case, and the delegator who can no longer hold tasks; `TareaServiceTest`: who completes, the claim by
  completing (timeline and audit) and the policy on and off; `MotorDeProcesosTest` and `MotorConTemporizadoresTest`:
  the delegation of a task the case leaves behind ends as `ANULADA`;
  `AsignacionAlCrearTest`: each kind of rule and each reason to fall back to the role tray.
- `ReglaDeAsignacionServiceImplTest`, `RevisionDeLasAsignaciones` through `DiagnosticoServiceTest` (`E-23`, `A-17`),
  `AsignacionBpmnTest` (schema, round trip and notes), `AplicacionDelModeloTest` and `HuellaDelDiagramaTest`.
- `CasoRepositoryTest`: the inbox queries, the atomic claim, the workload and the one open delegation per task
  (`uq_delegaciones_activa`) against PostgreSQL.
- `AsignacionIntegracionTest`: every kind of rule end to end, the rule frozen in the version, the fallback, saving the
  rule (roles, `400`, `409`), the rule in the exported BPMN, each action by HTTP and who may do it, the policy, the
  inboxes and the workload, `404` for another store's tasks, `delegaciones` behind row-level security, a cancelled case
  that closes its delegation, a test run with a delegated task that is purged with its delegations, and the race: a
  second claim that waits for the first and touches no row, and two claims at once five times with exactly one winner
  each.
- `AutorizacionPorRolTest` and `ReglaDeRecursoTest`: the new routes in the role matrix and their resource rules.

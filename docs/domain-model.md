[← Documentation](README.md)

# Domain model and rules

The code keeps the Spanish names of the original specification, and the API writes its error messages and history
entries in Spanish.

| Resource | Concept | Belongs to | Main attributes |
|---|---|---|---|
| `Empresa` | Store (tenant) | — | Name, NIT and contact email |
| `Usuario` | User | Store | Email (the login), access role (`ADMINISTRADOR`, `EDITOR` or `SOLO_LECTURA`) and status |
| `Proceso` | Process | Store | Name, description, category, state (`BORRADOR` or `PUBLICADO`), mode (`EJECUTABLE` or `DOCUMENTAL`), and its filing: folder, owner, next review, classification (`INTERNA`, `CONFIDENCIAL` or `RESTRINGIDA`) and tags |
| `Carpeta` | Folder | Store | Name and parent folder; sisters do not share a name, and the tree has at most eight levels |
| `Documento` | Document | Process | The element it is attached to (`bpmnId`, or none for the whole process), the name of its latest version and which one it is |
| `VersionDocumento` | Document version | Document | Number, cleaned name, type detected from the content, size, SHA-256, storage key, state (`PENDIENTE`, `DISPONIBLE` or `BLOQUEADO`) with its reason, and whether an antivirus reviewed it |
| `Documentacion` | Documentation | Process | The element it documents (`bpmnId`, or none for the whole process), its Markdown and the cleaned HTML |
| `Caracterizacion` | Characterization sheet | Process | Objective, scope, inputs, outputs, suppliers, customers, indicators, risks and regulation |
| `Manual` | Process manual | Process | The published version it describes, its language, state (`PENDIENTE`, `DISPONIBLE` or `FALLIDO`) with its reason, and where its HTML and PDF are |
| `Comentario` | Comment | Process | Its author, Markdown and cleaned HTML, the people it mentions, and its thread: none for the first comment, which carries the anchor (an element's `bpmnId`, a published version) and the state (open, or resolved with who and when); a deleted one keeps its place with no text |
| `Notificacion` | Notification | User | Its type (`MENCION`, `RESPUESTA` or `HILO_RESUELTO`), who caused it, the process and what it points to there, when it was read, and how its e-mail went (`INMEDIATO`, `RESUMEN_DIARIO` or `NUNCA`) and when |
| `PreferenciaNotificacion` | Notification preference | User | For one type, how it arrives by e-mail; without one, the type's default |
| `Concesion` | Grant | Store | An action (`VER`, `COMENTAR`, `MODELAR`, `REVISAR`, `APROBAR`, `PUBLICAR`, `EXPORTAR` or `ADMINISTRAR`) on a folder or a process, for a person, a process role or the whole store (`PERSONA`, `ROL` or `ORGANIZACION`) |
| `RolProceso` | Process role | Store | Name and description |
| `Pool` | Participant | Process | Type (`EMPRESA`, `CLIENTE`, `PROVEEDOR` or `SISTEMA_EXTERNO`), black-box flag, order and the kind of partner behind it (`integracion`) |
| `Lane` | Lane | Pool | Process role and order |
| `Actividad` · `Gateway` · `Evento` | Flow nodes | Lane | Name and position on the canvas. Activities add a description and a type (`USUARIO`, `SERVICIO`, `ENVIO` or `RECEPCION`), gateways a type (`EXCLUSIVO`, `PARALELO` or `INCLUSIVO`), and events a type (`INICIO`, `FIN`, `MENSAJE_INICIO`, `MENSAJE_INTERMEDIO` or `MENSAJE_FIN`). A user activity and a plain start event can carry a typed form (`Formulario`): its fields, in the order they are filled. A user activity can also say who its task goes to (`TipoAsignacion`: the role tray, a fixed person, the initiator or the person of a case variable). |
| `Arco` | Sequence flow | Pool | Source node, target node, label and condition |
| `Mensaje` | Message flow | Process | Sending and receiving pool, content, the nodes it is anchored to, how it travels (`CORREO`, `SERVICIO_WEB`, `COLA`), what the process does if it fails (`CONTINUAR`, `MANEJAR_ERROR`, `FINALIZAR`), the fields it carries, the name its body takes among the case variables, and the message that answers it |
| `Correlacion` | Correlation key | Message | The criterion that correlates the message, the field of the body that carries it, and what to do with a message that matches no open case |
| `HistorialCambio` | History entry | Process | Description, author and date |
| `Caso` | Case | Store | The process and the published version it runs on, the reference its messages are matched by, state (`ABIERTO`, `TERMINADO`, `CANCELADO`, `FALLIDO` or `ERROR`), mode (`PRODUCCION` on the wall clock and the business calendar, or `PRUEBA` on the store's ticks), the case variables as JSON and who opened it by hand, if anybody did |
| `ActividadCaso` | Step of a case | Case | The node of the version it went through, copied by id and by name, its kind, the process role of its lane, state (`PENDIENTE`, `EN_ESPERA`, `COMPLETADA`, `FALLIDA` or `OMITIDA`), how many tokens have reached it, who holds it (the only one who completes it), who delegated it to them, and what they handed over |
| `Delegacion` | Delegation | Step of a case | Who handed a task to whom, the reason, and how it ended (`ACTIVA`, `DEVUELTA`, `COMPLETADA` or `ANULADA`); at most one open per task |
| `EventoCaso` | Timeline entry | Case | What happened, when, and who caused it. Only inserted |
| `Temporizador` | Armed timer | Store | The process, version and timer event it comes from, the case and step that armed it (none for a timer start), class (`INICIO`, `INTERMEDIO` or `BORDE`), mode, expression, whether it interrupts, when it is due (an instant in `PRODUCCION`, a tick in `PRUEBA`), how many times it fired and state (`PROGRAMADO`, `DISPARADO`, `CANCELADO` or `FALLIDO`) |
| `Calendario` | Business calendar | Store | Country (`CO`, `US`, `FR` or `AU`), IANA time zone, working hours and working days. One per store, saved the first time an administrator changes it |
| `FestivoEditado` | Holiday change | Store | A day the store made a holiday or renamed (with its name), or a computed holiday it removed. The computed ones are not stored |
| `MembresiaRol` | Membership | Store | Which process roles a user belongs to; the pair is unique |

Every entity except `Empresa` extends `EntidadEmpresa`, which holds a mandatory `empresa_id` that cannot be updated.
Activities, gateways and events share one table through single-table inheritance, so a sequence flow can point to
any of them. The database makes each subtype fill its own type column and leaves the others empty.

Pools, lanes, nodes, sequence flows and message flows carry a `bpmn_id` that never changes, the `id` they take in a
BPMN file. Where each of them is drawn (BPMN DI) lives in `formas_bpmn` and `aristas_bpmn`: absolute bounds for each
pool, lane and node, the points of each flow and the bounds of each label. The server works them out from the
positions relative to the lane with the same calculation the web draws with, every time the process changes
([ADR-0052](adr/0052-geometry-in-the-domain.md)). With both, a process is exported as BPMN 2.0.2, and each published
version keeps its file in `versiones_proceso.modelo_bpmn` ([ADR-0053](adr/0053-bpmn-export.md)). A file imported from
another tool leaves what the model has no place for in `extension_xml`, per element, to write it back on export, and
each import leaves its report in `importaciones` ([ADR-0054](adr/0054-bpmn-import.md)). A process is executable or
documental (`procesos.modo`), and what only describes it lives in `artefactos` (annotations, groups, data objects and
stores) and `asociaciones`, whose ends are `bpmn_id`s ([ADR-0055](adr/0055-descriptive-bpmn-elements.md)). A node may
sit inside a sub-process (`padre_id`), and a call activity points to another process
([ADR-0056](adr/0056-subprocesses-and-call-activity.md)). `revisiones_borrador` keeps compressed save points of a
draft ([ADR-0040](adr/0040-etag-autosave-and-revisions.md)). Who has a process open, and who holds the lock to edit
it, live in `presencias` and `bloqueos_edicion` ([ADR-0058](adr/0058-presence-and-soft-lock.md)). Tags are an array
column of the process with a GIN index, so a page of the list reads them without another query
([ADR-0062](adr/0062-process-repository.md)), and grants live in `concesiones`, each one naming either a folder or a
process ([ADR-0063](adr/0063-granular-permissions.md)). Comments live in `comentarios`, their mentions in an array
column of ids, and notifications and their preferences in `notificaciones` and `preferencias_notificacion`; none
stores a name, so they show whatever name the person has now ([ADR-0067](adr/0067-comments-mentions-and-notifications.md)).

A version is `PUBLICADA` or `PRUEBA` (`versiones_proceso.tipo`): a test version freezes a draft to run it without
publishing, has number 0, is never listed nor in force, and the nightly purge deletes it with its cases. Timer events
say whether they wait a calendar duration, a business duration (`DURACION_HABIL`), a date or a cycle, and the store's
business calendar (`calendarios`, with its changes to the holidays in `festivos`) is what business time is counted with
([ADR-0072](adr/0072-business-calendar-and-timers.md)).

A form is a value of its node, not an entity: the JSON column `nodos_flujo.formulario`, frozen with the rest of the
diagram in the definition of each version. Its fields (`CampoDeFormulario`) have a name, a label, one of twelve types
and the validations and conditions that type admits ([ADR-0073](adr/0073-typed-forms.md)); the answers of a case are
not stored apart, they are its variables.

Who a task goes to is a value of its activity too: three columns of `nodos_flujo` frozen with the diagram, resolved when
a case creates the task ([ADR-0074](adr/0074-assignment-and-inbox.md)). From then on the task has a holder or waits in
its role tray; a delegation keeps who to give it back to.

## Modeling rules

- Only a person fills a form: only a user activity or a plain start event carries one, and a node with a form
  cannot change to another type while it has it. The same goes for an assignment, which only a user activity carries.
- Only whoever holds a task completes it; a member of the task's role, or an administrator, takes one nobody holds.
  An administrator completes another person's task only if the store's policy allows it. A condition of a field reads only the fields above it.
- A sequence flow joins two different nodes of the same pool, so it never crosses pools. There is at most one
  sequence flow from one node to another.
- A process starts at a start event and ends at an end event: no sequence flow arrives at a start event, and none
  leaves an end event. An event that already has flows cannot be turned into a type those flows forbid.
- A sequence flow that leaves an exclusive or inclusive gateway carries a condition, because the gateway picks its
  path by those conditions. The exception is its default flow, the one it takes when no condition holds: a gateway
  has at most one, it carries no condition, and only a gateway that decides has one. Flows that enter a gateway need
  no condition, and a gateway only becomes exclusive or inclusive when every flow that leaves it has one or is the
  default.
- A message flow connects two different pools, and both must be participants of the message's process.
- A message is anchored to the node that sends it and to the node that waits for it, each one in the pool of its
  side. Only a step that can do it: a message end event or an activity that sends or serves, on one side; a message
  start or intermediate event, or a receiving activity, on the other. A black-box pool anchors nothing, because its
  inside is not modeled.
- A message that handles a failure says which activity of the sending pool handles it, and only a message that
  handles its failure names one.
- The message that answers another one comes back from the pool that received it, in the same process.
- A step moves to any lane of its process while it is still loose. Once it has sequence flows or anchored
  messages it stays in its pool, because a flow never crosses pools and a message is anchored to the node of its own
  side. A lane of another process is never a place for it.
- Moving one end of a sequence flow goes through the same rules as connecting the two nodes for the first time.
- A participant drawn as a black box has no lanes, and one that already has lanes cannot become a black box.
- Turning a gateway parallel retires the conditions of its outgoing flows and its default flow, because a parallel
  gateway follows all of them: the history says how many were retired.
- Reordering the lanes of a pool, or the participants of a process, takes the complete list of their ids, so what
  a client shows after a drag is what gets saved.
- Flow-node names are unique within a process, including when a node is renamed.
- Process and process-role names are unique among a store's active records, ignoring case. The database enforces it
  too.
- A process role that an active process uses cannot be deleted.
- A published process cannot go back to draft.

## User rules

- User emails are unique across the platform and case-insensitive. The email is the login, and the login does not
  know the store yet.
- A store always keeps an active administrator. The last one cannot give up the role, and nobody can deactivate their
  own account. When two administrators remove each other's role at the same moment, the second change waits on a lock
  of the store's row, sees the first change and is refused with `409`.
- **Deactivating somebody is not deleting them.** They stop being able to sign in and the tokens already issued stop
  working, but the row stays: they keep signing the history entries they signed and the tasks they completed keep
  counting. `GET /api/v1/usuarios?incluirInactivos=true` is where they are found, and `PATCH /api/v1/usuarios/{id}`
  with `activo: true` brings them back.
- A user created without a password gets a temporary one. It is answered once, in the response that generates it,
  and never again: what the database keeps is its hash, like any other password.
- While a temporary password is in use, that user can only change it, log out or renew the token; everything else
  answers `403`. Changing it closes every session of the user and opens a new one, so the answer carries the tokens
  to keep.
- Passwords are at most 72 characters, which is what BCrypt reads.

## Lifecycle rules

- Publishing requires a diagnosis without errors. The `409` says how many there are and lists them in `errors`.
  Warnings do not block: a decision without a default flow is published, and the warning stays.
- Publishing saves the whole diagram as the next version, with the SHA-256 fingerprint of its canonical form. The
  fingerprint covers every field of every element and the name, description and category of the process, and leaves
  out what changes without the drawing changing: who saved it, when, and the optimistic version.
- Publishing again without having changed anything is refused with `409`, because the version would be identical.
- A version is never edited or deleted. It can be retired, and then the version in force is the newest one still
  standing; with none left, the process stays published but has nothing to show until it is published again.
  Version numbers are never reused.
- A published process cannot go back to draft.
- Everything is soft-deleted, from processes and process roles to every BPMN element. A deleted resource answers
  `404`, but it stays in the database, and an administrator can list and read the deleted processes with
  `incluirInactivos`.
- Deleting a pool retires the message flows that enter or leave it. Deleting a process (HU-06) retires its whole
  model.
- Every change to a process or its model is recorded in the process history with its author.

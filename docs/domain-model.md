[← Documentation](README.md)

# Domain model and rules

The code keeps the Spanish names of the original specification, and the API writes its error messages and history
entries in Spanish.

| Resource | Concept | Belongs to | Main attributes |
|---|---|---|---|
| `Empresa` | Store (tenant) | — | Name, NIT and contact email |
| `Usuario` | User | Store | Email (the login), access role (`ADMINISTRADOR`, `EDITOR` or `SOLO_LECTURA`) and status |
| `Proceso` | Process | Store | Name, description, category and state (`BORRADOR` or `PUBLICADO`) |
| `RolProceso` | Process role | Store | Name and description |
| `Pool` | Participant | Process | Type (`EMPRESA`, `CLIENTE`, `PROVEEDOR` or `SISTEMA_EXTERNO`), black-box flag, order and the kind of partner behind it (`integracion`) |
| `Lane` | Lane | Pool | Process role and order |
| `Actividad` · `Gateway` · `Evento` | Flow nodes | Lane | Name and position on the canvas. Activities add a description and a type (`USUARIO`, `SERVICIO`, `ENVIO` or `RECEPCION`), gateways a type (`EXCLUSIVO`, `PARALELO` or `INCLUSIVO`), and events a type (`INICIO`, `FIN`, `MENSAJE_INICIO`, `MENSAJE_INTERMEDIO` or `MENSAJE_FIN`). |
| `Arco` | Sequence flow | Pool | Source node, target node, label and condition |
| `Mensaje` | Message flow | Process | Sending and receiving pool, content, the nodes it is anchored to, how it travels (`CORREO`, `SERVICIO_WEB`, `COLA`), what the process does if it fails (`CONTINUAR`, `MANEJAR_ERROR`, `FINALIZAR`), the fields it carries, the name its body takes among the case variables, and the message that answers it |
| `Correlacion` | Correlation key | Message | The criterion that correlates the message, the field of the body that carries it, and what to do with a message that matches no open case |
| `HistorialCambio` | History entry | Process | Description, author and date |
| `Caso` | Case | Store | The process and the published version it runs on, the reference its messages are matched by, state (`ABIERTO`, `TERMINADO`, `CANCELADO`, `FALLIDO` or `ERROR`) and the case variables as JSON |
| `ActividadCaso` | Step of a case | Case | The node of the version it went through, copied by id and by name, its kind, the process role of its lane, state (`PENDIENTE`, `EN_ESPERA`, `COMPLETADA`, `FALLIDA` or `OMITIDA`), how many tokens have reached it, who took it and what they handed over |
| `EventoCaso` | Timeline entry | Case | What happened, when, and who caused it. Only inserted |
| `MembresiaRol` | Membership | Store | Which process roles a user belongs to; the pair is unique |

Every entity except `Empresa` extends `EntidadEmpresa`, which holds a mandatory `empresa_id` that cannot be updated.
Activities, gateways and events share one table through single-table inheritance, so a sequence flow can point to
any of them. The database makes each subtype fill its own type column and leaves the others empty.

## Modeling rules

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
  the editor shows after a drag is what gets saved.
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

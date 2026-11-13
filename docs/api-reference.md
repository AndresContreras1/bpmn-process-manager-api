[← Documentation](README.md)

# API reference

In `dev`, the interactive documentation is at `/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`. Every
operation documents what it does, what it returns and the errors it can answer, and a test fails the build when an
endpoint is left undocumented. The `prod` profile does not publish the documentation.

## Endpoints

| Resource | Endpoints |
|---|---|
| Stores | `POST /api/v1/empresas` · `GET /api/v1/empresas/actual` · `GET /api/v1/empresas/{id}` · `POST, DELETE /api/v1/empresas/actual/baja` · `PUT /api/v1/empresas/actual/preferencias` |
| Authentication | `GET /api/v1/auth/csrf` · `POST /api/v1/auth/login` · `POST /api/v1/auth/refresh` · `POST /api/v1/auth/logout` · `GET /api/v1/auth/sesion` · `GET, DELETE /api/v1/auth/sesiones` · `DELETE /api/v1/auth/sesiones/{id}` |
| Language and time zone | `GET, PUT /api/v1/auth/preferencias` (each person: empty follows the organization) · `PUT /api/v1/empresas/actual/preferencias` (administrators) |
| Users | `GET, POST /api/v1/usuarios` · `GET, PATCH, DELETE /api/v1/usuarios/{id}` · `GET, PUT /api/v1/usuarios/{id}/roles-proceso` · `POST /api/v1/usuarios/invitaciones` · `POST /api/v1/usuarios/{id}/anonimizar` |
| Processes | `GET, POST /api/v1/procesos` · `GET, PUT, PATCH, DELETE /api/v1/procesos/{id}` · `GET /api/v1/procesos/{id}/historial` |
| Folders | `GET, POST /api/v1/carpetas` · `PUT, DELETE /api/v1/carpetas/{id}` |
| Filing | `PUT /api/v1/procesos/{id}/ficha` · `GET /api/v1/procesos/etiquetas` |
| Documents | `GET, POST /api/v1/procesos/{id}/documentos` · `GET, DELETE /api/v1/documentos/{id}` · `POST /api/v1/documentos/{id}/versiones` · `GET /api/v1/documentos/{id}/enlace` · `GET /api/v1/descargas/documentos/{id}/{version}` |
| Documentation | `GET, PUT /api/v1/procesos/{id}/documentacion` · `POST /api/v1/documentacion/vista-previa` · `GET, PUT /api/v1/procesos/{id}/caracterizacion` |
| Manuals | `GET, POST /api/v1/procesos/{id}/manuales[?version=]` · `GET /api/v1/manuales/{id}` · `GET /api/v1/manuales/{id}/enlace?formato=pdf\|html` · `GET /api/v1/descargas/manuales/{id}/{formato}` · `PUT /api/v1/procesos/{id}/versiones/{numero}/dibujo` |
| Comments | `GET, POST /api/v1/procesos/{id}/comentarios[?bpmnId=&version=&estado=]` · `POST /api/v1/comentarios/{id}/respuestas` · `PUT, DELETE /api/v1/comentarios/{id}` · `POST /api/v1/comentarios/{id}/resolver` · `POST /api/v1/comentarios/{id}/reabrir` |
| Notifications | `GET /api/v1/notificaciones[?noLeidas=true]` · `GET /api/v1/notificaciones/conteo` · `POST /api/v1/notificaciones/{id}/leida` · `POST /api/v1/notificaciones/leidas` · `GET /api/v1/notificaciones/flujo` · `GET, PUT /api/v1/notificaciones/preferencias` |
| Permissions | `GET /api/v1/procesos/{id}/permisos` · `GET, POST /api/v1/procesos/{id}/concesiones` · `GET, POST /api/v1/carpetas/{id}/concesiones` · `DELETE /api/v1/concesiones/{id}` |
| Process roles | `GET, POST /api/v1/roles` · `GET, PUT, DELETE /api/v1/roles/{id}` |
| Pools | `GET, POST /api/v1/procesos/{procesoId}/pools` · `GET, PUT, DELETE /api/v1/pools/{id}` · `PUT /api/v1/procesos/{procesoId}/pools/orden` |
| Lanes | `GET, POST /api/v1/pools/{poolId}/lanes` · `GET, PUT, DELETE /api/v1/lanes/{id}` · `PUT /api/v1/pools/{poolId}/lanes/orden` |
| Activities | `GET, POST /api/v1/lanes/{laneId}/actividades` · `GET, PUT, DELETE /api/v1/actividades/{id}` |
| Gateways | `GET, POST /api/v1/lanes/{laneId}/gateways` · `GET, PUT, DELETE /api/v1/gateways/{id}` |
| Events | `GET, POST /api/v1/lanes/{laneId}/eventos` · `GET, PUT, DELETE /api/v1/eventos/{id}` |
| Assignment | `PUT /api/v1/actividades/{id}/asignacion` |
| Deadlines and escalation | `PUT /api/v1/actividades/{id}/plazo` · `PUT /api/v1/eventos/{id}/plazo` · `PUT /api/v1/eventos/{id}/escalamiento` |
| Forms | `PUT /api/v1/actividades/{id}/formulario` · `PUT /api/v1/eventos/{id}/formulario` · `GET /api/v1/procesos/{procesoId}/formulario-de-inicio` · `GET /api/v1/tareas/{id}/formulario` |
| Sequence flows | `POST /api/v1/arcos` · `GET /api/v1/pools/{poolId}/arcos` · `GET, PUT, DELETE /api/v1/arcos/{id}` |
| Message flows | `GET, POST /api/v1/procesos/{procesoId}/mensajes` · `GET, PUT, DELETE /api/v1/mensajes/{id}` |
| Correlation keys | `GET, PUT /api/v1/mensajes/{mensajeId}/correlacion` |
| Whole diagram | `GET /api/v1/procesos/{id}/diagrama` |
| Mode | `PUT /api/v1/procesos/{id}/modo` |
| Presence | `GET /api/v1/procesos/{id}/en-vivo` · `GET, POST .../bloqueo` · `POST .../bloqueo/latido`, `/soltar`, `/tomar-control` |
| Compare | `GET /api/v1/procesos/{id}/comparar?de=&a=` · `POST /api/v1/procesos/{id}/comparar` |
| Draft revisions | `GET, POST /api/v1/procesos/{id}/revisiones` · `GET .../{revisionId}/modelo` · `POST .../{revisionId}/restaurar` |
| Sub-processes | `PUT /api/v1/nodos/{id}/contenedor` · `PUT /api/v1/actividades/{id}/llamada` |
| Artifacts | `GET, POST /api/v1/procesos/{procesoId}/artefactos` · `GET, PUT, DELETE /api/v1/artefactos/{id}` |
| Associations | `GET, POST /api/v1/procesos/{procesoId}/asociaciones` · `DELETE /api/v1/asociaciones/{id}` |
| BPMN 2.0.2 | `GET, PUT /api/v1/procesos/{id}/modelo` · `POST /api/v1/procesos/importar[?nombre=]` · `GET /api/v1/procesos/{procesoId}/versiones/{numero}/modelo` |
| Published versions | `GET /api/v1/procesos/{procesoId}/versiones` · `GET, PATCH /api/v1/procesos/{procesoId}/versiones/{numero}` · `GET /api/v1/procesos/{procesoId}/versiones/{numero}/diagrama` |
| Diagnosis | `GET /api/v1/procesos/{id}/diagnostico[?sinElemento=TYPE:id]` |
| AI review | `POST /api/v1/procesos/{id}/revision` |
| Store history and settings | `GET /api/v1/empresas/actual/historial` · `GET, PUT /api/v1/empresas/actual/configuracion` |
| Passwords | `POST /api/v1/auth/password` · `POST /api/v1/usuarios/{id}/restablecer-clave` · `POST /api/v1/auth/recuperacion` · `POST /api/v1/auth/recuperacion/confirmar` |
| Second factor | `GET /api/v1/auth/segundo-factor` · `POST, DELETE /api/v1/auth/segundo-factor/totp` · `POST /api/v1/auth/segundo-factor/totp/confirmacion` · `POST /api/v1/auth/segundo-factor/codigos` · `POST /api/v1/auth/segundo-factor/verificacion` · `POST /api/v1/auth/reautenticacion` · `DELETE /api/v1/usuarios/{id}/segundo-factor` |
| Passkeys | `GET /api/v1/auth/segundo-factor/passkeys` · `POST /api/v1/auth/segundo-factor/passkeys/opciones` · `POST /api/v1/auth/segundo-factor/passkeys` · `PATCH, DELETE /api/v1/auth/segundo-factor/passkeys/{id}` · `POST /api/v1/auth/segundo-factor/verificacion/passkey/opciones` · `POST /api/v1/auth/segundo-factor/verificacion/passkey` · `POST /api/v1/auth/reautenticacion/passkey` · `POST /api/v1/auth/passkeys/opciones` · `POST /api/v1/auth/passkeys/entrada` |
| Sign in with a provider | `GET /api/v1/auth/proveedores` · `GET /api/v1/auth/oidc/{proveedor}` · `POST /api/v1/auth/identidades/vinculo` · `GET /api/v1/auth/identidades` · `DELETE /api/v1/auth/identidades/{id}` |
| Store domains | `GET, POST /api/v1/empresas/actual/dominios` · `POST /api/v1/empresas/actual/dominios/{id}/verificacion` · `DELETE /api/v1/empresas/actual/dominios/{id}` |
| Audit trail | `GET /api/v1/auditoria` · `GET /api/v1/auditoria/exportacion?formato=csv\|json` · `GET /api/v1/auditoria/verificacion` |
| Account by e-mail | `POST /api/v1/auth/verificacion` · `POST /api/v1/auth/verificacion/confirmar` · `POST /api/v1/auth/invitacion/aceptar` |
| Process sharing | `GET, POST /api/v1/procesos/{id}/compartidos` · `GET, DELETE /api/v1/procesos/{id}/compartidos/{empresaInvitadaId}` · `GET /api/v1/procesos/compartidos-conmigo` |
| Cases | `POST /api/v1/procesos/{procesoId}/casos` · `GET /api/v1/casos` · `GET /api/v1/casos/{id}` · `GET /api/v1/casos/{id}/eventos` · `POST /api/v1/casos/{id}/cancelar` · `PATCH /api/v1/casos/{id}/variables` · `POST /api/v1/casos/{id}/reintentar` |
| Test runs of a draft | `POST /api/v1/procesos/{procesoId}/pruebas` |
| Timers | `GET /api/v1/casos/{id}/temporizadores` · `GET /api/v1/procesos/{procesoId}/temporizadores` |
| Business calendar | `GET, PUT /api/v1/organizacion/calendario` · `GET /api/v1/organizacion/calendario/festivos?anio=` · `PUT, DELETE /api/v1/organizacion/calendario/festivos/{fecha}` · `GET /api/v1/organizacion/calendario/ediciones` · `DELETE /api/v1/organizacion/calendario/ediciones/{fecha}` |
| Tasks | `GET /api/v1/tareas?bandeja=` · `GET /api/v1/tareas/carga` · `GET /api/v1/tareas/{id}` · `POST /api/v1/tareas/{id}/completar` · `POST /api/v1/tareas/{id}/reclamar` · `POST /api/v1/tareas/{id}/soltar` · `POST /api/v1/tareas/{id}/reasignar` · `POST /api/v1/tareas/{id}/delegar` |
| Messaging | `POST /api/v1/procesos/{procesoId}/mensajes-entrantes` · `GET /api/v1/procesos/{procesoId}/bandeja-salida` · `GET /api/v1/procesos/{procesoId}/bandeja-entrada` · `GET /api/v1/casos/{id}/mensajes` |
| Simulation | `GET /api/v1/simulacion` · `POST /api/v1/simulacion/tick` · `POST /api/v1/simulacion/pedidos` |
| Dashboard | `GET /api/v1/procesos/{procesoId}/tablero` · `GET /api/v1/empresas/actual/tablero` |

`GET /api/v1/procesos/{id}/diagrama` returns everything a client needs to draw a process: the process and flat lists
of pools, lanes, activities, gateways, events, sequence flows, message flows and correlation keys, linked by id.
It runs one query per element type, however large the diagram grows. Every pool, lane, node and flow carries its
`bpmnId`, the id it has in a BPMN file, which never changes.

`GET /api/v1/procesos/{id}/modelo` returns the same process as a BPMN 2.0.2 file (`application/xml`, as an attachment
named after the process): a `collaboration` with a `participant` per pool and a `messageFlow` per message, a `process`
per modeled pool with its lanes, tasks, gateways, events and sequence flows, and a `BPMNDiagram` with the geometry the
model keeps. It validates against the OMG schemas and opens in bpmn.io and Camunda Modeler. Conditions are formal
expressions in the language `urn:bpmn-process-manager:condiciones:1`, and what BPMN has no element for (participant
kind and integration, lane role, flow order, and each message's content, fields, destination, failure handling,
answer and correlation) goes in `extensionElements` under `urn:bpmn-process-manager:bpm:1`, described by
[`bpm.xsd`](../src/main/resources/bpmn/xsd/bpm.xsd). The owning store gets its draft; a store the process is shared
with gets the version in force. `GET .../versiones/{numero}/modelo` answers the file written when that version was
published ([ADR-0053](adr/0053-bpmn-export.md)).

The way back takes a BPMN 2.0 file from this product or any other tool as the body (`application/xml`, up to 5 MB).
`POST /api/v1/procesos/importar` creates a draft process from it (`201`, with `Location`); `PUT
/api/v1/procesos/{id}/modelo` makes the draft what the file says, element by element by `bpmnId`, and importing the
file the process exports changes nothing. Both answer what was created, edited and deleted, and a report: what was
imported as it was, what was converted to the closest thing the model has and how, what was left out and why, whose
content from another tool was kept to export it again, and what the file breaks of the OMG schema. A file that breaks
the model's rules answers `409` with every problem; deleting needs an administrator, pools and lanes follow the
store's structure policy, and lanes asking for missing roles need an administrator, who creates them while importing
([ADR-0054](adr/0054-bpmn-import.md)).

A process is `EJECUTABLE` or `DOCUMENTAL`. `PUT /api/v1/procesos/{id}/modo` takes the mode and the `version` read, and
answers the process with the diagnosis of the new mode; it takes effect when the process is published again. A
documental version answers `409` to opening a case or receiving a message. Events take `tipoTemporizador` (`DURACION`,
`FECHA` or `CICLO`), `temporizador` (ISO 8601), and for a boundary event `adjuntoAId` and `interrumpe`
([ADR-0055](adr/0055-descriptive-bpmn-elements.md)).

```bash
# A Camunda Modeler file as a new process, and the same process back after editing it there
curl -s -X POST "http://localhost:8080/api/v1/procesos/importar?nombre=Returns" -b sesion.txt   -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/xml" --data-binary @returns.bpmn
curl -s -X PUT http://localhost:8080/api/v1/procesos/7/modelo -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"   -H "Content-Type: application/xml" --data-binary @returns.bpmn
```

State transitions use `PATCH`, for example `PATCH /api/v1/procesos/42` with `{ "estado": "PUBLICADO", "version": 3 }`,
which is what publishes a process and saves its version.

The `PUT` of an activity, a gateway or an event takes `laneId` to move it to another lane, and the `PUT` of a
sequence flow takes `origenId` and `destinoId` to reconnect it; an end that is left out keeps the one it had. The
two `orden` endpoints take `{ "ids": [...] }` with every child of the element, exactly once, in the order they
should be drawn. `GET /api/v1/procesos` and `GET /api/v1/procesos/{id}` take `incluirInactivos=true`, which only an
administrator can ask for.

The behaviour behind the endpoints lives with its topic: [diagnosis and versions](diagnosis-and-versions.md),
[cases, tasks, messages, simulation and dashboard](execution.md), and [authentication, passwords and
permissions](security.md).

## The process repository

Folders are a tree: `GET /api/v1/carpetas` answers all of them, each with its parent and how many processes sit
directly in it, and the client draws the tree. A folder is created with a name and an optional `padreId`, and `PUT` renames or moves
it with the `version` it was read with. Sister folders do not share a name, a folder cannot end up inside itself,
the tree has at most eight levels, and only an empty folder is deleted: `409` says what is still inside.

`PUT /api/v1/procesos/{id}/ficha` files a process: its `carpetaId`, its owner (`duenoId`, a user of the store), the
date of its `proximaRevision`, its `clasificacion` (`INTERNA`, `CONFIDENCIAL` or `RESTRINGIDA`) and up to ten
`etiquetas`, kept in lowercase, with the `version` read. It does not touch the model, so it neither changes the
fingerprint of the draft nor counts as a change waiting to be published ([ADR-0062](adr/0062-process-repository.md)).
`GET /api/v1/procesos/etiquetas` lists the tags in use, to suggest them.

`GET /api/v1/procesos` filters by all of it: `carpetaId` (that folder and everything inside it), `etiqueta`,
`duenoId`, `clasificacion` and `revisionAntesDe`, and sorts by `proximaRevision` and `clasificacion` too.
`GET /api/v1/roles/{id}` lists the processes whose lanes use the role.

```bash
curl -s -X PUT http://localhost:8080/api/v1/procesos/1/ficha -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" \
  -d '{"carpetaId":1,"duenoId":2,"proximaRevision":"2027-03-01","clasificacion":"CONFIDENCIAL","etiquetas":["logistics"],"version":0}'
```

## Documents

A document belongs to a process, or with `elemento=<bpmnId>` to one of its elements, and keeps every version. The
upload is the file itself as the body, not a form, with its name in `nombre`:

```bash
curl -s -X POST "http://localhost:8080/api/v1/procesos/1/documentos?nombre=Packing%20instructions.pdf"   -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/octet-stream" --data-binary @packing.pdf
```

The answer is `201` with the document `PENDIENTE`: the antivirus checks it in the background, and it turns
`DISPONIBLE` or `BLOQUEADO` with a `motivo`. `POST /api/v1/documentos/{id}/versiones` adds a version the same way.
`GET /api/v1/documentos/{id}/enlace[?version=]` answers a link that works without a session for five minutes, and
`409` while the version is pending or if it was blocked; the link is a plain `GET` that downloads the file as an
attachment. A file over 25 MB, or over what is left of the store's quota, answers `413`; one whose content is not an
accepted type answers `415`, whatever its name says ([Security](security.md#documents)).

## Documentation and the manual

`PUT /api/v1/procesos/{id}/documentacion` takes `{ "elemento": "<bpmnId>", "markdown": "...", "version": n }`: the
text of the whole process without `elemento`, or of one element. It answers the Markdown and the `html` to show, which
the server converted and cleaned: HTML written inside the Markdown comes out as text, and there are no images, styles,
scripts or links other than http, https and mailto. An empty text removes it. `POST /api/v1/documentacion/vista-previa`
answers the same HTML without saving it, for a preview. `GET, PUT /api/v1/procesos/{id}/caracterizacion` read and write
the characterization sheet: objective, scope, inputs, outputs, suppliers, customers, indicators, risks and regulation.

Right after publishing, the web app sends the drawing of the new version with `PUT .../versiones/{numero}/dibujo`
(the SVG of bpmn-js, up to 4 MB): the server keeps a cleaned copy, once. `POST /api/v1/procesos/{id}/manuales` asks for
the manual of the version in force, or of `?version=n`, in the language of whoever asks: it answers `202` with the
manual `PENDIENTE`, and the job queue builds it as HTML and PDF — the sheet, the drawing, the documentation of the
process and of every step lane by lane, and the documents available. `GET /api/v1/manuales/{id}/enlace?formato=pdf`
(or `html`) answers a five-minute signed link, like a document's.

## The business calendar and timers

`GET /api/v1/organizacion/calendario` answers the store's calendar: `pais` (`CO`, `US`, `FR` or `AU`), `zonaHoraria`,
`horaInicio`, `horaFin`, `diasHabiles` and `version`. Until an administrator saves one it is the default of the
store's country, without a version. `PUT` replaces it whole, administrators only, with the `version` read (none the
first time); a zone that does not exist or a working day that ends before it starts answers `400`, and an old version
`409`:

```json
{ "pais": "FR", "zonaHoraria": "Europe/Paris", "horaInicio": "09:00", "horaFin": "18:00",
  "diasHabiles": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"], "version": 0 }
```

`GET .../festivos?anio=2026` lists the holidays of a year, from 1900 to 2199, each with `fecha`, `nombre` and
`origen` (`CALCULADO` from the country's law, or `EDITADO`). Administrators change them by day:
`PUT .../festivos/{fecha}` with `{ "nombre": "King's Birthday" }` makes a day a holiday or renames it;
`DELETE .../festivos/{fecha}` removes one (`409` if the day is not a holiday); `DELETE .../ediciones/{fecha}` undoes the
store's change to that day (`404` if there is none). `GET .../ediciones` lists the changes, not paged: a store changes
at most 500 days, and a new one past that answers `409`. Anyone reads the calendar.

A case opens in `PRUEBA` unless it asks for `{"modo": "PRODUCCION"}` in `POST /api/v1/procesos/{id}/casos`; the mode
comes back in every case. `GET /api/v1/casos/{id}/temporizadores` lists the timers its waits and the boundaries of its
activities armed: `clase`, `modo`, `tipoTemporizador`, `expresion`, `interrumpe`, `venceEn` (UTC, in `PRODUCCION`) or
`venceTick` (in `PRUEBA`), `repeticiones`, `disparos`, `estado` and, for one that failed, `ultimoError` (a fixed reason
with the exception's class; the details stay in the server log). `GET /api/v1/procesos/{id}/temporizadores?pagina=&tamano=`
pages the timer starts of a process, newest first (20 by default, at most 50), in the usual page envelope: each publish
arms new ones and cancels those of the previous version, so the list grows with the process. A process that starts with
a timer opens its cases on its own, and opening one by hand answers `409`.

Timer expressions have caps, checked by the diagnosis (`E-16`) and again when a timer is armed: a duration up to a
hundred years, a business duration (`DURACION_HABIL`) up to 366 working days or 366 days of working hours, dates from
1900 to 9999, and a cycle at most once a minute (an ISO period of a minute or more; a cron expression with a fixed
seconds field, or a macro such as `@hourly`).

`POST /api/v1/procesos/{id}/pruebas`, with an optional `referencia` and `variables`, tries the draft without publishing
it: it answers `201` with a `PRUEBA` case on a test version (`versionNumero` 0) that no version list shows. The draft has
to pass the diagnosis (`409` with the errors), and whoever asks needs `MODELAR` on the process. Test-run cases are
listed with the others and their tasks reach the trays, so whoever tests can walk each path; incoming messages never
correlate with them, and the dashboard does not count them. How the timers work is
in [ADR-0072](adr/0072-business-calendar-and-timers.md) and [Execution](execution.md).

## Typed forms

A user task and a plain start event can carry a form. `PUT /api/v1/actividades/{id}/formulario` (or
`/eventos/{id}/formulario`) replaces it whole with the node's `version`; `"campos": []` removes it. Administrators and
editors, with `MODELAR` on the process:

```json
{ "version": 3, "campos": [
  { "nombre": "aprobado", "etiqueta": "Approved?", "tipo": "SI_NO", "requerido": true },
  { "nombre": "monto", "etiqueta": "Amount", "tipo": "MONEDA", "moneda": "COP", "maximo": 5000000,
    "porDefecto": "order.total", "requeridoSi": "aprobado == true" },
  { "nombre": "motivo", "etiqueta": "Why not", "tipo": "TEXTO_LARGO", "visibleSi": "aprobado == false" },
  { "nombre": "codigo", "etiqueta": "Code", "tipo": "TEXTO", "patron": "[A-Z]{3}-[0-9]{4}", "largoMaximo": 8 },
  { "nombre": "canal", "etiqueta": "Channel", "tipo": "LISTA",
    "opciones": [ { "valor": "WEB", "etiqueta": "Web" }, { "valor": "TIENDA", "etiqueta": "Store" } ] } ] }
```

Each field has a `nombre` (the variable it fills: a letter, then letters, digits or `_`, up to 60, unique and not
`and`, `or`, `not`, `true`, `false`, `caso` or `tarea`), an `etiqueta`, a `tipo` (`TEXTO`, `TEXTO_LARGO`, `NUMERO`,
`MONEDA`, `FECHA`, `FECHA_HORA`, `SI_NO`, `LISTA`, `LISTA_MULTIPLE`, `PERSONA`, `ARCHIVO` or `SECCION`) and an optional
`ayuda`. What each type admits:

| Property | Types | Meaning |
|---|---|---|
| `requerido`, `requeridoSi` | every type but `SECCION` | Must be answered while visible; `requeridoSi` is a condition |
| `minimo`, `maximo` | `NUMERO`, `MONEDA`, `LISTA_MULTIPLE` | The value, or how many options are chosen |
| `largoMinimo`, `largoMaximo` | `TEXTO` (up to 1000), `TEXTO_LARGO` (up to 10000) | Characters, counted as people count them |
| `patron` | `TEXTO`, `TEXTO_LARGO` | An RE2 expression the whole text matches, up to 200 characters |
| `desde`, `hasta` | `FECHA`, `FECHA_HORA` | ISO 8601 limits, both included; a `FECHA_HORA` is a wall-clock time, with no zone |
| `moneda` | `MONEDA` | ISO 4217 code; it sets the decimals (two without one) |
| `opciones` | `LISTA`, `LISTA_MULTIPLE` | 1 to 200, each with a unique `valor` and an `etiqueta` |
| `porDefecto` | every type but `SECCION` | A case variable whose value is offered, such as `order.total` |
| `visibleSi` | every type | A condition; on a section it hides every field up to the next section |

A field comes back in one shape whichever way it was sent: a blank text, `requerido: false` and an empty `opciones`
come back absent, and a limit comes back without trailing zeros or exponent (`1E+3` and `1000.00` are `1000`), which is
also how it travels in BPMN.

Conditions use the [gateway grammar](execution.md#running-a-process) and read the case variables and the fields above
by name. A form that is not well built answers `400` with each property in `errors` (`campos[1].patron`, with a
back-reference or a repetition that grows too much refused); a form on another kind of node, `409`. Every answer that
carries a form — the activity or event, the diagram, a version's diagram and the two reads below — brings each condition
compiled next to its text, for the web to evaluate without parsing it:

```json
"visibleSiArbol": { "tipo": "Y", "partes": [
  { "tipo": "COMPARACION", "partes": [], "ruta": "aprobado", "operador": "==", "valor": false },
  { "tipo": "NO", "partes": [ { "tipo": "COMPARACION", "partes": [], "ruta": "order.vip", "operador": "==", "valor": true } ] } ] }
```

`GET /api/v1/tareas/{id}/formulario` answers the form frozen in the version of the task's case, the `valores` offered
in each field and the case `variables` the conditions read. `GET /api/v1/procesos/{id}/formulario-de-inicio` answers
the start form of the version in force (`404` without one). Any role reads both.

`POST /api/v1/tareas/{id}/completar` and `POST /api/v1/procesos/{id}/casos` check `datos` and `variables` against that
form. A wrong answer is a `400` with the field under its body property, and nothing is saved:

```json
{ "title": "Validación fallida", "status": 400, "detail": "Las respuestas no cumplen el formulario.",
  "errors": { "datos.aprobado": "Es true o false.", "datos.monto": "Debe ser como mucho 5000000.",
              "datos.cupon": "No es un campo de este formulario." } }
```

What passes is stored with its type under `tarea.<taskNameInCamel>`: numbers as numbers, yes/no as a boolean, dates as
ISO 8601 text, a multiple list as a list, a person or a file as its id (an active person of the store; a document of
the process whose latest version is available). A hidden field is dropped. Without a form, free key-value data is
taken only by a `PRUEBA` case; a `PRODUCCION` one answers `400` for each key. The form also travels in BPMN as
`bpm:formulario` ([ADR-0073](adr/0073-typed-forms.md)).

## Assignment and inboxes

A user task can say who it goes to. `PUT /api/v1/actividades/{id}/asignacion` saves the rule on the draft with the
activity's `version`; administrators and editors, with `MODELAR` on the process:

```json
{ "tipo": "CAMPO", "campo": "tarea.reviewOrder.approver", "version": 4 }
```

| `tipo` | Who gets the task when a case reaches it | Needs |
|---|---|---|
| `ROL` | Nobody yet: it waits in the tray of its lane's process role, as before. Allowed on any activity | — |
| `PERSONA` | A fixed person | `usuarioId`, an active administrator or editor of the store |
| `INICIADOR` | Whoever opened the case by hand | — |
| `CAMPO` | The person a case variable names, such as a `PERSONA` field of the start form (`approver`) or of an earlier task (`tarea.reviewOrder.approver`) | `campo`, written like a condition names a variable, up to 200 characters |

A person who cannot complete tasks, or a `campo` that is not a variable path, is a `400` with the property in `errors`;
anything but `ROL` on an activity that is not a user task is a `409`. The activity answers its rule in `asignacion`
(absent for the role tray); it is frozen in the version when it is published, travels in BPMN as `bpm:asignacion`,
and the diagnosis reports `E-23` for a rule that cannot work and `A-17` for a variable no `PERSONA` field writes. When a
case cannot tell the person, or that person can no longer complete tasks, the task waits in its role tray and the case
timeline says why.

Every task has a holder (`asignadoA`) or waits in its role tray. Only the holder completes it:

- `POST /api/v1/tareas/{id}/completar` on a task nobody holds takes it for the caller, if the caller belongs to its
  process role or is an administrator (`403` otherwise); on a task another person holds it answers `409`, unless the
  caller is an administrator and the store turned on `administradorCompletaPorOtro`. Taking a task by completing it
  leaves the same `TAREA_RECLAMADA` line and audit event as claiming it first.
- `POST /api/v1/tareas/{id}/reclamar`, no body: a member of its role, or an administrator, takes a task nobody holds.
  If somebody took it first, even an instant before, `409`.
- `POST /api/v1/tareas/{id}/soltar`, with an optional `{ "motivo": "…" }`: the holder, or an administrator, lets it go.
  It goes back to the role tray, or, when the holder is a delegate, back to whoever delegated it, if that person can
  still hold tasks (the role tray otherwise, and the timeline says why).
- `POST /api/v1/tareas/{id}/reasignar` with `{ "usuarioId": 5, "motivo": "Covering the weekend shift" }`: the holder,
  or an administrator, hands it to another active administrator or editor; an administrator can also give a task
  nobody holds. A delegated task is reassigned by whoever delegated it, or an administrator, never by the delegate
  (`403`). An open delegation ends.
- `POST /api/v1/tareas/{id}/delegar` with the same body: only the holder lends it to another person, who completes it
  or releases it back. The answer carries `delegadaPor`. A delegated task is not delegated again (`409`).

A task that no longer waits, or whose case is in error or closed, answers `409`; a task of another store, `404`; a
person of another store, `404`; one who cannot complete tasks, `409`. Each action goes to the case timeline
(`TAREA_RECLAMADA`, `TAREA_LIBERADA`, `TAREA_REASIGNADA`, `TAREA_DELEGADA`, with the reason) and to the audit trail.
The reason is free text that anonymizing a person does not rewrite: it is for the why, not for names. The web's tray
calls `reclamar` and `soltar` for its "Take" and "Release" buttons, and shows "Complete" only on a task the viewer
holds or nobody holds.

`GET /api/v1/tareas?bandeja=` picks the inbox, the one due first at the top (production tasks by `venceEn`, then test
runs by `venceTick`, then the tasks without a deadline, oldest first), with the filters `rolProcesoId`, `procesoId` and
`estado` as before:

| `bandeja` | Tasks |
|---|---|
| `TODAS` (default) | The whole store |
| `MIAS` | The ones the caller holds |
| `DE_MIS_ROLES` | The ones of the process roles the caller belongs to, whoever holds them; `mias=true` is the same |
| `SIN_ASIGNAR` | The ones nobody holds |

`GET /api/v1/tareas/carga` (optionally `?procesoId=`) answers the workload: how many waiting tasks nobody holds and how
many each person holds, the busiest first; test runs of a draft do not count. Any role reads the inboxes and the
workload:

```json
{ "sinAsignar": 3, "personas": [ { "usuarioId": 5, "nombre": "Luis Gomez", "tareas": 4 } ] }
```

## Deadlines and escalation

A user task can have a deadline, and so can the whole case, on its start event. `PUT /api/v1/actividades/{id}/plazo`
and `PUT /api/v1/eventos/{id}/plazo` save it on the draft with the node's `version`; administrators and editors, with
`MODELAR` on the process:

```json
{ "cantidad": 2, "unidad": "DIAS_HABILES", "version": 4 }
```

`unidad` is `HORAS` (wall-clock hours, 1 to 8784) or `DIAS_HABILES` (business days of the organization's calendar, 1 to
366: two business days from a Friday at three are the Tuesday at three, without the weekend or a holiday). Without
`cantidad` and `unidad` the node has none. A quantity out of range, or one of the two without the other, is a `400` on
the property; a deadline on an activity that is not a user task, or on an event that is not a start event, a `409`. The
node answers it in `plazo` (absent without one); it is frozen in the version and travels in BPMN as `bpm:plazo`.

When a case reaches the task, or opens through that start event, the deadline starts to count. Tasks and cases answer
`venceEn` (UTC, in `PRODUCCION`) or `venceTick` (in `PRUEBA`, a tick per hour, and a business day as many ticks as the
working day has hours) and `estadoPlazo`: `A_TIEMPO`, `POR_VENCER` from the last quarter of the deadline, and `VENCIDA`
once it passed. A completed task keeps the state it reached: `VENCIDA` means it was completed late. A scheduler moves
the states every `PLAZOS_REPASO` in production, and the store's tick in test mode; each change writes a line in the case
timeline (`PLAZO_POR_VENCER`, `PLAZO_VENCIDO`) and one notification per person, once: to whoever holds the task, or the
members of its role while nobody does, and to the process owner; for a case, to whoever opened it and the owner.

A non-interrupting boundary timer can escalate the task it hangs from. `PUT /api/v1/eventos/{id}/escalamiento`:

```json
{ "accion": "REASIGNAR", "usuarioId": 5, "version": 2 }
```

When the timer fires, `AVISAR` notifies the holder and the supervisor; `REASIGNAR` hands the task to the supervisor
(the delegation it had ends) and notifies both. The supervisor is `usuarioId`, an active administrator or editor
(`400` otherwise), or the process owner when it is empty. Without `accion` the timer only opens its path, as before.
Escalating from an event that is not a non-interrupting timer boundary answers `409`, and so does `REASIGNAR` on a
boundary of something that is not a user task. The rule holds afterwards: `PUT /actividades/{id}` that changes the type
of an activity a `REASIGNAR` boundary hangs from, and `PUT /eventos/{id}` that attaches such a boundary to an activity
of another type, answer `409` until the escalation is `AVISAR`. An interrupting boundary timer already takes the task out and the case
follows its path. Each escalation writes a `TAREA_ESCALADA` line; a reassignment also goes to the audit trail. It
travels in BPMN as `bpm:escalamiento`.

The three notification types `PLAZO_POR_VENCER`, `PLAZO_VENCIDO` and `TAREA_ESCALADA` reach the notification center
and its stream, and are e-mailed at once unless the person chooses otherwise in `/notificaciones/preferencias`; their
`referenciaId` is the case, and the e-mail links to it. `GET /api/v1/procesos/{id}/tablero` and
`GET /api/v1/empresas/actual/tablero` answer the compliance in `cumplimiento` ([Execution](execution.md#the-dashboard)),
over the processes the caller may see: a process a grant hides is neither named nor counted. The dashboard of another
store's process, or of one that does not exist, answers `404`.

```json
"cumplimiento": { "tareasATiempo": 40, "tareasTarde": 5, "casos": { "aTiempo": 30, "tarde": 2 },
  "porProceso": [ { "procesoId": 1, "procesoNombre": "Refunds", "aTiempo": 40, "tarde": 5 } ],
  "porActividad": [ { "procesoId": 1, "procesoNombre": "Refunds", "nodoId": 12, "nodoNombre": "Review refund",
                      "aTiempo": 18, "tarde": 3 } ],
  "porPersona": [ { "usuarioId": 5, "nombre": "Luis Gomez", "aTiempo": 12, "tarde": 1 } ] }
```

```bash
curl -s -X PUT http://localhost:8080/api/v1/actividades/12/plazo -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"cantidad":8,"unidad":"HORAS","version":4}'
curl -s -X PUT http://localhost:8080/api/v1/eventos/15/escalamiento -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"accion":"AVISAR","version":2}'
```

How deadlines are counted and why they are not timers is in
[ADR-0075](adr/0075-deadlines-and-escalation.md).

## Comments and notifications

A thread starts with `POST /api/v1/procesos/{id}/comentarios`:

```json
{ "markdown": "Who checks the **address** before packing?", "bpmnId": "Activity_0b3k9xq",
  "versionDelProceso": 2, "menciones": [2, 5] }
```

Only `markdown` is required, up to 10 000 characters, converted and cleaned like the documentation. `bpmnId` anchors
the thread to an element of the draft (`404` if it has none), or with `versionDelProceso` to an element of that
published version (`404` if the version does not exist). `menciones` are ids of people of the store who can view
the process, at most 20; any other id answers `400` with `errors` naming its position (`"menciones[1]"`) and the same
reason for every case, so it never says whether someone exists. The answer is `201` with the comment: its `html`, the
`autor` and the `menciones` with their names, `estado` (`ABIERTO` or `RESUELTO`, only on the first comment of a
thread) and the `version` to send back when editing.

`POST /api/v1/comentarios/{id}/respuestas` takes `{ "markdown", "menciones" }`; replying to a reply answers in its
thread. `PUT /api/v1/comentarios/{id}` takes the same with the `version` read, and only its author edits it (`403`,
`409` with a stale version). `POST .../resolver` and `.../reabrir` act on the first comment of a thread (`409` on a
reply, on a resolved thread or on an open one). `DELETE` is for its author or an administrator: the comment loses its
text and keeps its place, so a thread with replies stays, marked `eliminado`. `GET /api/v1/procesos/{id}/comentarios`
answers a page of threads, newest first and up to 50, each with its replies oldest first, filtered by `bpmnId`,
`version` and `estado`. Every role comments, the read-only one too; reading needs `VER` and writing `COMENTAR` and
`VER` on the process ([Security](security.md#comments-and-notifications)).

Who hears about it: those mentioned (`MENCION`), everyone else who wrote in the thread when someone replies
(`RESPUESTA`), and everyone who wrote in it when it is resolved (`HILO_RESUELTO`), never the author. Each one gets a
notification that only they see; another's answers `404`.

| Route | Answers |
|---|---|
| `GET /api/v1/notificaciones?noLeidas=true&pagina=0&tamano=20` | A page, newest first and up to 50: `tipo`, `procesoId` and `proceso`, `referenciaId` (the comment; the case for a deadline or an escalation), `autor` (empty when the system sent it), `creadaEn`, `leidaEn` |
| `GET /api/v1/notificaciones/conteo` | `{ "noLeidas": 3 }` |
| `POST /api/v1/notificaciones/{id}/leida` · `POST /api/v1/notificaciones/leidas` | The notification read · the count, now `0` |
| `GET /api/v1/notificaciones/flujo` | Server-sent events: `conteo` when it opens and whenever it changes, `notificacion` with each new one, and a heartbeat every 25 seconds; at most five per person on each instance (`429`) |
| `GET, PUT /api/v1/notificaciones/preferencias` | Each type with `envio`: `INMEDIATO`, `RESUMEN_DIARIO` or `NUNCA`, and whether it is the default. `PUT` takes `{ "preferencias": [{ "tipo": "HILO_RESUELTO", "envio": "NUNCA" }] }` and leaves the other types as they were |

By default a mention and a reply are e-mailed at once, and a resolved thread waits for the daily digest. An e-mail is
written in the person's language, says who did what in which process with a link to it, and never carries the
comment's text; one that is already read when its e-mail would leave is not sent.

```bash
curl -s -X POST http://localhost:8080/api/v1/procesos/1/comentarios -b sesion.txt -H "X-XSRF-TOKEN: $XSRF"   -H "Content-Type: application/json" -d '{"markdown":"Who checks the address?","menciones":[2]}'
curl -N http://localhost:8080/api/v1/notificaciones/flujo -b sesion.txt
```

## Permissions

A grant is an action on a folder or a process for a subject: `{ "accion": "MODELAR", "sujeto": "PERSONA",
"usuarioId": 2 }`, with `ROL` and `rolProcesoId` for the people of a process role, or `ORGANIZACION` for everyone in
the store. The actions are `VER`, `COMENTAR`, `MODELAR`, `REVISAR`, `APROBAR`, `PUBLICAR`, `EXPORTAR` and
`ADMINISTRAR`. Listing, granting and revoking need `ADMINISTRAR` on the resource. `GET /api/v1/procesos/{id}/permisos`
answers what the person asking can do with a process, so a client shows only the buttons that will work. How the
grants are decided is in [Security](security.md#permissions-on-folders-and-processes).

## The store's own history and settings

`GET /api/v1/empresas/actual/historial` answers everything that happened in the store, newest first and paginated:
users created, renamed, given another role or deactivated, process roles added and removed, the registration of the
store itself, and every change to a process. Each entry says who did it, when, and what it was about, with
`recursoTipo` and `recursoId`. Administrators only.

`GET` and `PUT /api/v1/empresas/actual/configuracion` read and change what the store decides about itself.
`politicaEstructura`: with `SOLO_ADMINISTRADOR`, creating and editing participants and lanes is reserved to
administrators, and editors keep modeling steps, flows and messages inside a lane. `inactividadSesionMinutos`, from
30 to 60, and `duracionSesionHoras`, from 1 to 24: how long a session lasts without being renewed, and how long
after its login it ends ([Authentication](security.md#authentication)); left out, they keep their values.
`administradorCompletaPorOtro`, off by default: an administrator completes a task another person holds, and the audit
trail records it each time; left out, it keeps its value. Every change is recorded in the history.

```bash
curl -s -X PUT http://localhost:8080/api/v1/empresas/actual/configuracion -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"politicaEstructura":"SOLO_ADMINISTRADOR","version":0}'
```

## Closing the store

`POST /api/v1/empresas/actual/baja` with `{"confirmacion": "<the store's name>"}` closes the caller's store and
answers it with `bajaSolicitadaEn` and `borradoProgramadoPara`, 30 days later. Until then the store is read-only,
every change answers `409` with the title "Tienda dada de baja", and `DELETE /api/v1/empresas/actual/baja` cancels
the closing; then everything of the store is deleted ([The life of the data](security.md#the-life-of-the-data)).
Administrators only, like `POST /api/v1/usuarios/{id}/anonimizar`, which deletes the personal data of a user on
request.

## Sessions

The login answers the session in two `HttpOnly` cookies and the user's profile in the body; no body carries a
token. A browser keeps the cookies and sends them back by itself. A request that changes something with them, and
the login itself, also sends the CSRF token: the value of the `XSRF-TOKEN` cookie, which `GET /api/v1/auth/csrf`
sets, in the header `X-XSRF-TOKEN`. Without it the answer is `403`. [Authentication](security.md#authentication)
has the detail.

Any other client plays the browser. With curl, a cookie jar does it, and the examples of these pages use it:

```bash
curl -s -c sesion.txt http://localhost:8080/api/v1/auth/csrf
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' sesion.txt)
curl -s -b sesion.txt -c sesion.txt -X POST http://localhost:8080/api/v1/auth/login -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"email":"admin@demo.com","password":"every-order-on-time"}'
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' sesion.txt)
curl -s -b sesion.txt http://localhost:8080/api/v1/procesos
```

The CSRF token lasts the session. Every way in (the login, the code of the second factor, a passkey, a provider)
and logging out set a new one, so the snippet reads it again after the login; nothing else changes it, renewing the
session included.

The cookies are `Secure`: like a browser, curl sends them over plain HTTP only to `localhost`. The refresh cookie
lives the store's idle timeout, and `expiresIn` in the body says when the access cookie expires: fifteen minutes,
or less near the end of the session.

With a second factor the login answers `segundoFactorPendiente: true` and only the access cookie, for five minutes.
The code finishes it, with the same cookie jar, and sets the token of the session:

```bash
curl -s -b sesion.txt -c sesion.txt -X POST http://localhost:8080/api/v1/auth/segundo-factor/verificacion \
  -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" -d '{"codigo":"492039"}'
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' sesion.txt)
```

## Links sent by e-mail

Verifying an e-mail, recovering a password and accepting an invitation start with an e-mail that carries a link
to the web app, with the token after `#token=`. The web app sends that token to the API, to
`/verificacion/confirmar`, `/recuperacion/confirmar` or `/invitacion/aceptar`, which need no session. The web app
receives them at `/verificar-correo`, `/restablecer-clave` and `/aceptar-invitacion`. Each link works once and expires, and a link that does not work answers `400` with
the title "Enlace no válido", whatever the reason.

## Pagination

Lists that can grow (processes, process roles, users and shared processes) accept these parameters:

| Parameter | Description |
|---|---|
| `pagina` | Page number, starting at 0 |
| `tamano` | Page size, from 1 to 50. The default is 10. |
| `orden` | A field from the list's allowlist with `asc` or `desc`, such as `orden=nombre,asc` |

The id breaks ties, so no row repeats or goes missing between pages. Processes can also be filtered by `nombre`,
`estado` and `categoria`, process roles by `nombre` (HU-20), and users by `nombre` and `incluirInactivos`. Every
list answers the same envelope:

```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 2, "totalPages": 1 }
```

## Concurrent edits

Every editable resource answers a `version` that increases with each saved change. A `PUT` or `PATCH` sends back the
version it read. If someone saved a change since, the request answers `409` and changes nothing, so the client can
reload and decide again. When two edits of the same version arrive at the same time, both pass that check and the
database rejects the second one through JPA's `@Version`. The correlation key of a message is the only upsert: the
first one is created without a version.

Every `GET` of an editable resource carries an `ETag`, and an edit may send `If-Match` next to its `version`: a stale
one answers `412` instead of `409`. On a whole model, the tag is the SHA-256 of its BPMN file
([ADR-0040](adr/0040-etag-autosave-and-revisions.md)).

**Autosave.** `PUT /procesos/{id}/modelo` with `If-Match` and an `Idempotency-Key` is how the modeler saves: one save
at a time per process, nothing written when the file changes nothing, and a `412` with the current fingerprint and the
latest changes when someone saved first. Every few minutes of editing, on closing the editor and just before a
restore, the draft leaves a revision: `GET /procesos/{id}/revisiones` lists them, `.../{revisionId}/modelo` downloads
one and `.../restaurar` brings it back. A revision is never published and opens no cases.

**Who is editing.** `GET /procesos/{id}/en-vivo` is a stream of server-sent events — `presencia`, `bloqueo`,
`guardado`, `publicado` and `control-tomado` — that every replica forwards, since they travel through PostgreSQL's
`LISTEN/NOTIFY`. The first person who edits takes a soft lock and keeps it with heartbeats; 60 seconds without one
and it is gone. Anyone else may take control, which is audited and told to the previous editor. The lock does not
stop a save: `If-Match` does ([ADR-0058](adr/0058-presence-and-soft-lock.md)).

**Comparing.** `GET /procesos/{id}/comparar?de=&a=` takes a version number, `vigente` or `borrador` on each side,
and the `POST` takes a file, which is what a client compares after a `412`. The answer has four lists matched by
`bpmnId`: added, removed, changed, with each field before and after, and moved
([ADR-0057](adr/0057-semantic-diff.md)).

## Idempotent requests

An authenticated `POST` accepts an `Idempotency-Key` header with any unique value, such as a UUID. A retry with the
same key gets the first response back, marked with `Idempotent-Replayed: true`, instead of creating the resource
again. The same key with a different request answers `422`, and while the first request is still running, `409`. Only
successful responses are kept, so after an error the client can fix the request and retry with the same key. Keys
belong to each user.

## Auditing

Every editable resource answers `creadoPor`, `fechaCreacion`, `modificadoPor` and `fechaModificacion`, which Spring
Data auditing fills from the authenticated user. Records that the system creates without a token, such as the first
administrator of a store, have no author.

## Errors

Errors follow RFC 9457 (Problem Details):

```json
{
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "Proceso no encontrado.",
  "instance": "/api/v1/procesos/99",
  "requestId": "3f2b9c1e-6a4d-4f7e-9b1a-0c2d8e5f7a61",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Every response carries an `X-Request-Id` header, and every error repeats it as `requestId`: it is the id of the
request in the logs, so it is what to quote when reporting a problem. `traceId` is the id of its trace, for whoever
looks at it in a tracing backend. A request that already brings an
`X-Request-Id`, from a proxy in front or from a client, keeps it when it is up to 64 letters, digits, dots,
underscores or hyphens; otherwise the API assigns a new one. The web container of the Compose stack sends the id
NGINX gives each request.

Errors that happen outside a controller, such as a failure inside a filter, come back in the same format through
the server's error page. A `500` never says what failed inside: `"title": "Error interno"` and a generic `detail`,
while the exception goes to the log under the same `requestId`.

A `400` for specific fields adds an `errors` map, so a client can show each message next to its field. It covers
failed validations, values of the wrong type (for enums, the message lists the valid values) and fields that the
operation does not accept:

```json
{
  "title": "Validación fallida",
  "status": 400,
  "detail": "Uno o más campos no son válidos.",
  "instance": "/api/v1/procesos",
  "errors": {
    "categoria": "La categoria es obligatoria.",
    "nombre": "El nombre es obligatorio."
  }
}
```

A business rule that the request would break answers `409` with the reason in `detail`. Every `401` carries
`WWW-Authenticate: Bearer`.

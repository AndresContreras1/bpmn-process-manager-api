[← Documentation](README.md)

# API reference

In `dev`, the interactive documentation is at `/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`. Every
operation documents what it does, what it returns and the errors it can answer, and a test fails the build when an
endpoint is left undocumented. The `prod` profile does not publish the documentation.

## Endpoints

| Resource | Endpoints |
|---|---|
| Stores | `POST /api/v1/empresas` · `GET /api/v1/empresas/actual` · `GET /api/v1/empresas/{id}` |
| Authentication | `GET /api/v1/auth/csrf` · `POST /api/v1/auth/login` · `POST /api/v1/auth/refresh` · `POST /api/v1/auth/logout` |
| Users | `GET, POST /api/v1/usuarios` · `GET, PATCH, DELETE /api/v1/usuarios/{id}` · `GET, PUT /api/v1/usuarios/{id}/roles-proceso` · `POST /api/v1/usuarios/invitaciones` |
| Processes | `GET, POST /api/v1/procesos` · `GET, PUT, PATCH, DELETE /api/v1/procesos/{id}` · `GET /api/v1/procesos/{id}/historial` |
| Process roles | `GET, POST /api/v1/roles` · `GET, PUT, DELETE /api/v1/roles/{id}` |
| Pools | `GET, POST /api/v1/procesos/{procesoId}/pools` · `GET, PUT, DELETE /api/v1/pools/{id}` · `PUT /api/v1/procesos/{procesoId}/pools/orden` |
| Lanes | `GET, POST /api/v1/pools/{poolId}/lanes` · `GET, PUT, DELETE /api/v1/lanes/{id}` · `PUT /api/v1/pools/{poolId}/lanes/orden` |
| Activities | `GET, POST /api/v1/lanes/{laneId}/actividades` · `GET, PUT, DELETE /api/v1/actividades/{id}` |
| Gateways | `GET, POST /api/v1/lanes/{laneId}/gateways` · `GET, PUT, DELETE /api/v1/gateways/{id}` |
| Events | `GET, POST /api/v1/lanes/{laneId}/eventos` · `GET, PUT, DELETE /api/v1/eventos/{id}` |
| Sequence flows | `POST /api/v1/arcos` · `GET /api/v1/pools/{poolId}/arcos` · `GET, PUT, DELETE /api/v1/arcos/{id}` |
| Message flows | `GET, POST /api/v1/procesos/{procesoId}/mensajes` · `GET, PUT, DELETE /api/v1/mensajes/{id}` |
| Correlation keys | `GET, PUT /api/v1/mensajes/{mensajeId}/correlacion` |
| Whole diagram | `GET /api/v1/procesos/{id}/diagrama` |
| Published versions | `GET /api/v1/procesos/{procesoId}/versiones` · `GET, PATCH /api/v1/procesos/{procesoId}/versiones/{numero}` · `GET /api/v1/procesos/{procesoId}/versiones/{numero}/diagrama` |
| Diagnosis | `GET /api/v1/procesos/{id}/diagnostico[?sinElemento=TYPE:id]` |
| AI review | `POST /api/v1/procesos/{id}/revision` |
| Store history and settings | `GET /api/v1/empresas/actual/historial` · `GET, PUT /api/v1/empresas/actual/configuracion` |
| Passwords | `POST /api/v1/auth/password` · `POST /api/v1/usuarios/{id}/restablecer-clave` · `POST /api/v1/auth/recuperacion` · `POST /api/v1/auth/recuperacion/confirmar` |
| Account by e-mail | `POST /api/v1/auth/verificacion` · `POST /api/v1/auth/verificacion/confirmar` · `POST /api/v1/auth/invitacion/aceptar` |
| Process sharing | `GET, POST /api/v1/procesos/{id}/compartidos` · `GET, DELETE /api/v1/procesos/{id}/compartidos/{empresaInvitadaId}` · `GET /api/v1/procesos/compartidos-conmigo` |
| Cases | `POST /api/v1/procesos/{procesoId}/casos` · `GET /api/v1/casos` · `GET /api/v1/casos/{id}` · `GET /api/v1/casos/{id}/eventos` · `POST /api/v1/casos/{id}/cancelar` · `PATCH /api/v1/casos/{id}/variables` · `POST /api/v1/casos/{id}/reintentar` |
| Tasks | `GET /api/v1/tareas` · `GET /api/v1/tareas/{id}` · `POST /api/v1/tareas/{id}/completar` · `POST /api/v1/tareas/{id}/asignar` |
| Messaging | `POST /api/v1/procesos/{procesoId}/mensajes-entrantes` · `GET /api/v1/procesos/{procesoId}/bandeja-salida` · `GET /api/v1/procesos/{procesoId}/bandeja-entrada` · `GET /api/v1/casos/{id}/mensajes` |
| Simulation | `GET /api/v1/simulacion` · `POST /api/v1/simulacion/tick` · `POST /api/v1/simulacion/pedidos` |
| Dashboard | `GET /api/v1/procesos/{procesoId}/tablero` · `GET /api/v1/empresas/actual/tablero` |

`GET /api/v1/procesos/{id}/diagrama` returns everything a client needs to draw a process: the process and flat lists
of pools, lanes, activities, gateways, events, sequence flows, message flows and correlation keys, linked by id.
It runs one query per element type, however large the diagram grows.

State transitions use `PATCH`, for example `PATCH /api/v1/procesos/42` with `{ "estado": "PUBLICADO", "version": 3 }`,
which is what publishes a process and saves its version.

The `PUT` of an activity, a gateway or an event takes `laneId` to move it to another lane, and the `PUT` of a
sequence flow takes `origenId` and `destinoId` to reconnect it; an end that is left out keeps the one it had. The
two `orden` endpoints take `{ "ids": [...] }` with every child of the element, exactly once, in the order they
should be drawn. `GET /api/v1/procesos` and `GET /api/v1/procesos/{id}` take `incluirInactivos=true`, which only an
administrator can ask for.

The behaviour behind the endpoints lives with its topic: [diagnosis and versions](diagnosis-and-versions.md),
[cases, tasks, messages, simulation and dashboard](execution.md), and [authentication and
passwords](security.md).

## The store's own history and settings

`GET /api/v1/empresas/actual/historial` answers everything that happened in the store, newest first and paginated:
users created, renamed, given another role or deactivated, process roles added and removed, the registration of the
store itself, and every change to a process. Each entry says who did it, when, and what it was about, with
`recursoTipo` and `recursoId`. Administrators only.

`GET` and `PUT /api/v1/empresas/actual/configuracion` read and change what the store decides about itself. Today
that is `politicaEstructura`: with `SOLO_ADMINISTRADOR`, creating and editing participants and lanes is reserved to
administrators, and editors keep modeling steps, flows and messages inside a lane. Changing it is recorded in the
history.

```bash
curl -s -X PUT http://localhost:8080/api/v1/empresas/actual/configuracion -b sesion.txt -H "X-XSRF-TOKEN: $XSRF" \
  -H "Content-Type: application/json" -d '{"politicaEstructura":"SOLO_ADMINISTRADOR","version":0}'
```

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
curl -s -b sesion.txt http://localhost:8080/api/v1/procesos
```

The cookies are `Secure`: like a browser, curl sends them over plain HTTP only to `localhost`.

## Links sent by e-mail

Verifying an e-mail, recovering a password and accepting an invitation start with an e-mail that carries a link
to the web app, with the token after `#token=`. The web app sends that token to the API, to
`/verificacion/confirmar`, `/recuperacion/confirmar` or `/invitacion/aceptar`, which need no session. The
screens of the web app that receive those links arrive with the account screens; until then the token can be
sent to the API directly. Each link works once and expires, and a link that does not work answers `400` with
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

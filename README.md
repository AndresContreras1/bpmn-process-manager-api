# BPMN Process Manager API

**Model, validate and share the workflows behind every online order.**

BPMN Process Manager is a multi-tenant platform where online stores document how their orders move, from checkout to
delivery, payments and returns, as BPMN process diagrams. Each store works in a private workspace, its team gets
role-based access, and every change is validated and recorded. This repository contains the REST API and an Angular
web app built on top of it.

[![CI](https://github.com/AndresContreras1/bpmn-process-manager-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/AndresContreras1/bpmn-process-manager-api/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Spring Security 7](https://img.shields.io/badge/Spring%20Security-7%20%C2%B7%20JWT-6DB33F?logo=springsecurity&logoColor=white)
![Hibernate 7.4](https://img.shields.io/badge/Hibernate-7.4-59666C?logo=hibernate&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-prod-4169E1?logo=postgresql&logoColor=white)
![Angular 19](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

> [!NOTE]
> The platform designs and validates processes. It does not execute them: there are no live orders, rule engines, or
> calls to real payment providers or carriers.

## Contents

**Product**

1. [Overview](#overview)
2. [How it works](#how-it-works)
3. [Example: order fulfillment](#example-order-fulfillment)
4. [Roles and permissions](#roles-and-permissions)
5. [Built-in guarantees](#built-in-guarantees)

**Technical documentation**

6. [Getting started](#getting-started)
7. [Configuration](#configuration)
8. [Domain model and rules](#domain-model-and-rules)
9. [Security](#security)
10. [API reference](#api-reference)
11. [Architecture](#architecture)
12. [Quality and testing](#quality-and-testing)
13. [Design decisions](#design-decisions)
14. [Roadmap](#roadmap)
15. [Credits](#credits)

## Overview

### The problem

A single online order crosses several teams and outside companies: the storefront, a payment provider, the warehouse
and a carrier. When that workflow only lives in people's heads, handoffs break. Payments get captured for orders that
never ship, returns stall between teams, and every new hire learns the process by trial and error.

### The solution

The platform turns each workflow into a shared model that everyone reads the same way: who does each step, in which
order, where decisions are made, and what information is exchanged with customers and partners. Models use BPMN
(Business Process Model and Notation), the standard notation for business processes (ISO/IEC 19510), so any analyst
can read them.

### Who it is for

| Audience | What they get |
|---|---|
| Store owners and operations managers | One up-to-date map of how orders are handled, with the history of every change |
| Process editors and team leads | A modeling workspace that rejects design mistakes the moment they are made |
| Partner companies | Read-only access to the processes that a store decides to share with them |
| Developers | A documented REST API for back-office tools, with the included web app as a first client |

### Key concepts

| Term | Meaning | Example |
|---|---|---|
| Process | A workflow that the store runs again and again | Order fulfillment |
| Participant (pool) | A company or system that takes part in the process | The store, the customer, the payment gateway |
| Lane | A team or role inside a participant | Sales, Warehouse |
| Activity | A unit of work | Pick and pack items |
| Gateway | A point where the flow splits or merges. When it splits, an exclusive gateway takes exactly one path, an inclusive gateway takes every path whose condition holds, and a parallel gateway takes all of them. | Payment approved? |
| Sequence flow | The order of the steps inside a participant, with an optional condition | Payment approved? → Pick and pack items |
| Message flow | Information exchanged between two participants | Payment authorization request |
| Correlation key | The value that ties together the messages of one case | `orderId` |

## How it works

1. **Register the store.** The store gets its private workspace and its first administrator.
2. **Invite the team.** The administrator adds users and gives each one an access level: administrator, editor or
   read-only.
3. **Define process roles.** Roles describe who does the work, such as *Sales* or *Warehouse*, and every process of
   the store can reuse them.
4. **Model the process.** Editors add the participants, a lane for each role, the activities and gateways, the order
   between them, and the messages exchanged with other participants.
5. **Validate as you go.** Every change is checked against the modeling rules. A change that would break them is
   rejected with the reason, so a model never ends up in an invalid state.
6. **Publish.** When the process is ready, it moves from draft to published. A published process cannot go back to
   draft.
7. **Share.** An administrator can give a partner company on the platform read-only access to a process, for example
   a logistics provider that needs to see how orders are handed over.
8. **Keep track.** Every change is recorded in the process history with its author and date. Deleted items are
   retired, not erased, so the record stays complete.

The web app in [`frontend/`](frontend/) covers signing in, store registration, the account page, the process list,
detail and forms, publishing, and a diagram viewer. Modeling the diagram itself is done through the
[API](#api-reference).

## Example: order fulfillment

The platform starts with a demo store, *Demo Store*. It has a published *Order fulfillment* process that uses every
element of the notation, and a draft *Returns and refunds* process that is ready to be modeled. Sign in as
`admin@demo.com` with the password `admin123` (see [Getting started](#getting-started)).

**Participants**

| Participant | Type | Detail |
|---|---|---|
| Demo Store | The store | Two lanes: *Sales* and *Warehouse* |
| Customer | Customer | Black box: the store does not model its internals |
| Payment gateway | External system | Black box |
| Carrier | Supplier | Black box |

**Steps**

| # | Step | Lane | What happens |
|---|---|---|---|
| 1 | Receive order | Sales | Validate the cart, the stock and the shipping address. |
| 2 | Request payment authorization | Sales | Send the order total to the payment gateway. |
| 3 | *Payment approved?* | Sales | Exclusive gateway. `payment.status == APPROVED` continues to step 4, and `payment.status == DECLINED` goes to step 6. |
| 4 | Pick and pack items | Warehouse | Collect the items and prepare the package. |
| 5 | Ship order | Warehouse | Hand the package over to the carrier. |
| 6 | Cancel order | Sales | Release the reserved stock and notify the customer. |

**Messages**, all correlated by `orderId`

| Message | From | To | Content |
|---|---|---|---|
| Order placed | Customer | Demo Store | Cart items, shipping address and payment method |
| Payment authorization request | Demo Store | Payment gateway | Order total and tokenized card |
| Payment authorization result | Payment gateway | Demo Store | Approved or declined, with the transaction id |
| Shipment request | Demo Store | Carrier | Package size, weight and delivery address |
| Order status notification | Demo Store | Customer | Confirmation with the tracking number, or the cancellation notice |

## Roles and permissions

| Capability | Administrator | Editor | Read-only |
|---|:---:|:---:|:---:|
| View processes, process roles and diagrams | ✓ | ✓ | ✓ |
| Create and edit processes and diagrams | ✓ | ✓ | — |
| Delete processes and diagram elements | ✓ | — | — |
| Manage process roles | ✓ | — | — |
| Manage users | ✓ | — | — |
| Share a process with a partner company | ✓ | — | — |

A store always keeps at least one active administrator, and nobody can deactivate their own account. Changing a
user's access level or deactivating them takes effect at once: their open sessions are closed.

## Built-in guarantees

| Guarantee | What it means for the business |
|---|---|
| Private workspace | A store's data is only visible to its own users. A request for another store's data is answered as if the data did not exist. |
| Access that follows the role | Each person can only do what their access level allows, and a change of role or a deactivation applies immediately. |
| Protected sign-in | Sign-in tokens are short-lived, a copied token is detected and its session closed, and repeated failed sign-ins are paused. |
| No lost work | When two people edit the same item, the second save is refused instead of silently overwriting the first. |
| No duplicates on retries | A create request that is retried with the same idempotency key, for example after a network failure, creates the item only once. |
| Always-valid models | The modeling rules are checked on every change, not only when a process is published. |
| Complete history | Every change keeps its author and date, and deleted items stay on record. |

The [technical documentation](#getting-started) explains how each guarantee is built.

## Getting started

### Requirements

- JDK 21, or Docker, for the API
- Node.js 22 or later for the web app (optional)

### Run the API

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080` with the `dev` profile:

- The database is an H2 file under `./data`, created with *Demo Store* on the first start.
- Swagger UI is at `/swagger-ui.html`, and the OpenAPI document at `/v3/api-docs`.
- The H2 console is at `/h2-console` (JDBC URL `jdbc:h2:file:./data/procesos`, user `sa`, no password).

If `JWT_SECRET` is not set, a random signing key is generated. Access tokens then stop working after a restart, and
the refresh token, which is stored in the database, renews them.

> [!IMPORTANT]
> If you ran a version from before Flyway, delete `./data` once. Flyway builds the schema on the next start, and it
> does not adopt a schema that Hibernate created.

### First requests

The examples use `jq` to read the token.

```bash
# Sign in as the Demo Store administrator
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"email":"admin@demo.com","password":"admin123"}' | jq -r .accessToken)

# List the store's processes
curl -s http://localhost:8080/api/v1/procesos -H "Authorization: Bearer $TOKEN"

# Read a whole process: participants, lanes, steps, flows and messages
curl -s http://localhost:8080/api/v1/procesos/{id}/diagrama -H "Authorization: Bearer $TOKEN"

# Register another store; its processes and Demo Store's are invisible to each other
curl -s -X POST http://localhost:8080/api/v1/empresas -H "Content-Type: application/json" \
  -d '{"nombreEmpresa":"Acme Store","nit":"901234567-8","correoContacto":"contact@acme.com","nombreAdmin":"Ana","emailAdmin":"ana@acme.com","passwordAdmin":"secret123"}'
```

The login also returns a `refreshToken`. Before the access token expires, exchange it for a new pair with
`POST /api/v1/auth/refresh` and the body `{"refreshToken": "..."}`.

### Run the web app

```bash
cd frontend
npm ci
npm start
```

The app opens on `http://localhost:4200`, and the Angular dev server forwards the `/api` calls to the API on port
8080. [frontend/README.md](frontend/README.md) describes its structure and conventions.

### Postman collection

The [Postman collection](postman/) covers a second scenario, in which *Acme Store* models how it hands orders over to
a third-party logistics (3PL) partner. Run the requests in order, one by one or with the Collection Runner. The last
folder deletes what the scenario created, children first.

### Docker

```bash
docker build -t bpmn-process-manager-api .
docker run -p 8080:8080 \
  -e JWT_SECRET=<at-least-32-random-characters> \
  -e SPRING_DATASOURCE_URL=jdbc:h2:mem:procesos \
  bpmn-process-manager-api
```

The image is a multi-stage build that runs as a non-root user. This command starts the `dev` profile on an in-memory
database with Demo Store. For persistent data, use the `prod` profile with PostgreSQL.

## Configuration

### Profiles

| Profile | Activated by | Database | Demo Store | H2 console | OpenAPI and Swagger UI | SQL log |
|---|---|---|:---:|:---:|:---:|:---:|
| `dev` | Default, when no profile is set | H2 file under `./data` | ✓ | ✓ | ✓ | ✓ |
| `test` | `@ActiveProfiles("test")` in integration tests | In-memory H2, a new one for each Spring test context | — | — | ✓ | — |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | PostgreSQL | — | — | — | — |

### Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` uses PostgreSQL and leaves out the demo store and the API documentation | `dev` |
| `DB_HOST` · `DB_PORT` · `DB_NAME` | Database location | `localhost` · `5432` · `procesos` |
| `DB_USER` · `DB_PASSWORD` | Database credentials | `procesos` · empty |
| `JWT_SECRET` | HS256 signing key of at least 32 bytes. The `prod` profile does not start without it. | None |
| `JWT_EXPIRATION_SECONDS` | Access token lifetime | `900` |
| `JWT_REFRESH_EXPIRATION_SECONDS` | Refresh token lifetime. Every renewal issues a new refresh token. | `604800` (7 days) |
| `LOGIN_MAX_FAILED_ATTEMPTS` · `LOGIN_FAILED_ATTEMPTS_WINDOW` | Failed logins for an email from one address before `429`, and the window that counts them | `5` · `15m` |
| `CORS_ALLOWED_ORIGINS` | Allowed storefront or back-office origins | `http://localhost:4200` |

On startup, Flyway creates the schema or brings it up to date. The database must exist, and its user needs permission
to create tables.

## Domain model and rules

The code keeps the Spanish names of the original specification, and the API writes its error messages and history
entries in Spanish.

| Resource | Concept | Belongs to | Main attributes |
|---|---|---|---|
| `Empresa` | Store (tenant) | — | Name, NIT and contact email |
| `Usuario` | User | Store | Email (the login), access role (`ADMINISTRADOR`, `EDITOR` or `SOLO_LECTURA`) and status |
| `Proceso` | Process | Store | Name, description, category and state (`BORRADOR` or `PUBLICADO`) |
| `RolProceso` | Process role | Store | Name and description |
| `Pool` | Participant | Process | Type (`EMPRESA`, `CLIENTE`, `PROVEEDOR` or `SISTEMA_EXTERNO`), black-box flag and order |
| `Lane` | Lane | Pool | Process role and order |
| `Actividad` · `Gateway` | Flow nodes | Lane | Name and position on the canvas. Activities add a description, and gateways a type: `EXCLUSIVO`, `PARALELO` or `INCLUSIVO`. |
| `Arco` | Sequence flow | Pool | Source node, target node, label and condition |
| `Mensaje` | Message flow | Process | Sending pool, receiving pool and content |
| `Correlacion` | Correlation key | Message | The criterion that correlates the message, such as `orderId` |
| `HistorialCambio` | History entry | Process | Description, author and date |

Every entity except `Empresa` extends `EntidadEmpresa`, which holds a mandatory `empresa_id` that cannot be updated.
Activities and gateways share one table through single-table inheritance, so a sequence flow can point to either.

### Modeling rules

- A sequence flow joins two different nodes of the same pool, so it never crosses pools. There is at most one
  sequence flow from one node to another.
- A sequence flow that leaves an exclusive or inclusive gateway carries a condition, because the gateway picks its
  path by those conditions. Flows that enter a gateway need none, and a gateway only becomes exclusive or inclusive
  when every flow that leaves it has a condition.
- A message flow connects two different pools, and both must be participants of the message's process.
- Flow-node names are unique within a process, including when a node is renamed.
- Process and process-role names are unique among a store's active records, ignoring case. The database enforces it
  too.
- A process role that an active process uses cannot be deleted.
- A published process cannot go back to draft.

### User rules

- User emails are unique across the platform and case-insensitive. The email is the login, and the login does not
  know the store yet.
- A store always keeps an active administrator. The last one cannot give up the role, and nobody can deactivate their
  own account. When two administrators remove each other's role at the same moment, the second change waits on a lock
  of the store's row, sees the first change and is refused with `409`.

### Lifecycle rules

- Everything is soft-deleted, from processes and process roles to every BPMN element. A deleted resource answers
  `404`, but it stays in the database.
- Deleting a pool retires the message flows that enter or leave it. Deleting a process (HU-06) retires its whole
  model.
- Every change to a process or its model is recorded in the process history with its author.

## Security

### Authentication

1. `POST /api/v1/empresas` registers a store together with its first administrator.
2. `POST /api/v1/auth/login` checks the credentials through Spring Security's `AuthenticationManager` and opens a
   session with two tokens:
   - The **access token** is a JWT signed with `JWT_SECRET` that expires after 15 minutes. Its claims are the email,
     `usuarioId`, `empresaId`, the role and the session (`sid`).
   - The **refresh token** is 256 random bits. The database stores only its SHA-256 hash, so a copy of the database
     cannot open a session.
3. Clients send `Authorization: Bearer <access token>`. The filter builds the principal from the claims, without a
   database query, and rejects the tokens of a closed session.
4. `POST /api/v1/auth/refresh` exchanges the refresh token for a new pair in the same session. Each refresh token
   works once. A refresh token that was already used means that a copy exists, so the whole session is closed.
5. `POST /api/v1/auth/logout` closes the session. Deactivating a user or changing their role closes all of their
   sessions, so the old role stops working at once.

Public endpoints are limited to store registration, login, token renewal, the API documentation (not published in
`prod`) and, in `dev`, the H2 console. The role matrix in [Roles and permissions](#roles-and-permissions) is defined in
one place, the security configuration, and decides `401` or `403` before any controller runs.

### Login protection

An unknown email, a deactivated user and a wrong password get the same `401`, and `DaoAuthenticationProvider` spends
the time of a BCrypt comparison even when the email does not exist. After 5 failed attempts for an email from the same
address within 15 minutes, the login answers `429` with `Retry-After` and stops checking passwords until the oldest
attempt leaves the window. Because attempts are counted per email and address, an attacker elsewhere cannot lock the
real user out.

Closed sessions and failed attempts are kept in memory, and closed sessions are reloaded from the database on
startup. A deployment with several instances would move both to a shared store such as Redis.

### Data isolation

A platform that hosts many stores must never show one store's data to another. The design enforces this instead of
relying on each query to remember a filter:

```java
@NoRepositoryBean
public interface RepositorioTenant<T extends EntidadEmpresa> extends JpaRepository<T, Long> {
    Optional<T> findByIdAndEmpresaId(Long id, Long empresaId);
    List<T> findAllByEmpresaId(Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
```

- Every tenant repository extends `RepositorioTenant`, and ArchUnit fails the build if a service calls the unfiltered
  `findById` or `findAll`.
- Ids that arrive in a request body are resolved the same way. A lane cannot point to another store's process role,
  and a sequence flow cannot connect another store's nodes.
- No request DTO carries an `empresaId`, which ArchUnit also checks. The client never chooses the tenant, and a
  request body that still sends one is rejected with `400`.
- Access to another store's resource answers `404`, not `403`, so the API does not confirm that the resource exists.
- An integration suite creates two stores and has one try to read, change or link the other's resources (50 cases).

**Read-only sharing (HU-23).** An administrator can share a process with another store by its NIT. A process then has
two doors: the write door finds only the store's own processes, and the read door also finds the ones shared with it.
The whole diagram is the only endpoint behind the read door, and it is marked `compartido: true` for the guest. The
detail, the history and every modeling endpoint stay private to the owner, and any change from the guest answers
`404`. The guest lists what it received in `GET /api/v1/procesos/compartidos-conmigo`, and the owner's process history
records when a process was shared and when the sharing ended.

## API reference

In `dev`, the interactive documentation is at `/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`. Every
operation documents what it does, what it returns and the errors it can answer, and a test fails the build when an
endpoint is left undocumented. The `prod` profile does not publish the documentation.

### Endpoints

| Resource | Endpoints |
|---|---|
| Stores | `POST /api/v1/empresas` · `GET /api/v1/empresas/actual` · `GET /api/v1/empresas/{id}` |
| Authentication | `POST /api/v1/auth/login` · `POST /api/v1/auth/refresh` · `POST /api/v1/auth/logout` |
| Users | `GET, POST /api/v1/usuarios` · `GET, PATCH, DELETE /api/v1/usuarios/{id}` |
| Processes | `GET, POST /api/v1/procesos` · `GET, PUT, PATCH, DELETE /api/v1/procesos/{id}` · `GET /api/v1/procesos/{id}/historial` |
| Process roles | `GET, POST /api/v1/roles` · `GET, PUT, DELETE /api/v1/roles/{id}` |
| Pools | `GET, POST /api/v1/procesos/{procesoId}/pools` · `GET, PUT, DELETE /api/v1/pools/{id}` |
| Lanes | `GET, POST /api/v1/pools/{poolId}/lanes` · `GET, PUT, DELETE /api/v1/lanes/{id}` |
| Activities | `GET, POST /api/v1/lanes/{laneId}/actividades` · `GET, PUT, DELETE /api/v1/actividades/{id}` |
| Gateways | `GET, POST /api/v1/lanes/{laneId}/gateways` · `GET, PUT, DELETE /api/v1/gateways/{id}` |
| Sequence flows | `POST /api/v1/arcos` · `GET /api/v1/pools/{poolId}/arcos` · `GET, PUT, DELETE /api/v1/arcos/{id}` |
| Message flows | `GET, POST /api/v1/procesos/{procesoId}/mensajes` · `GET, PUT, DELETE /api/v1/mensajes/{id}` |
| Correlation keys | `GET, PUT /api/v1/mensajes/{mensajeId}/correlacion` |
| Whole diagram | `GET /api/v1/procesos/{id}/diagrama` |
| Process sharing | `GET, POST /api/v1/procesos/{id}/compartidos` · `GET, DELETE /api/v1/procesos/{id}/compartidos/{empresaInvitadaId}` · `GET /api/v1/procesos/compartidos-conmigo` |

`GET /api/v1/procesos/{id}/diagrama` returns everything a client needs to draw a process: the process and flat lists
of pools, lanes, activities, gateways, sequence flows, message flows and correlation keys, linked by id. It runs one
query per element type, however large the diagram grows.

State transitions use `PATCH`, for example `PATCH /api/v1/procesos/42` with `{ "estado": "PUBLICADO", "version": 3 }`.

### Pagination

Lists that can grow (processes, process roles, users and shared processes) accept these parameters:

| Parameter | Description |
|---|---|
| `pagina` | Page number, starting at 0 |
| `tamano` | Page size, from 1 to 50. The default is 10. |
| `orden` | A field from the list's allowlist with `asc` or `desc`, such as `orden=nombre,asc` |

The id breaks ties, so no row repeats or goes missing between pages. Processes can also be filtered by `nombre`,
`estado` and `categoria`, and process roles by `nombre` (HU-20). Every list answers the same envelope:

```json
{ "content": [ ... ], "page": 0, "size": 10, "totalElements": 2, "totalPages": 1 }
```

### Concurrent edits

Every editable resource answers a `version` that increases with each saved change. A `PUT` or `PATCH` sends back the
version it read. If someone saved a change since, the request answers `409` and changes nothing, so the client can
reload and decide again. When two edits of the same version arrive at the same time, both pass that check and the
database rejects the second one through JPA's `@Version`. The correlation key of a message is the only upsert: the
first one is created without a version.

### Idempotent requests

An authenticated `POST` accepts an `Idempotency-Key` header with any unique value, such as a UUID. A retry with the
same key gets the first response back, marked with `Idempotent-Replayed: true`, instead of creating the resource
again. The same key with a different request answers `422`, and while the first request is still running, `409`. Only
successful responses are kept, so after an error the client can fix the request and retry with the same key. Keys
belong to each user.

### Auditing

Every editable resource answers `creadoPor`, `fechaCreacion`, `modificadoPor` and `fechaModificacion`, which Spring
Data auditing fills from the authenticated user. Records that the system creates without a token, such as the first
administrator of a store, have no author.

### Errors

Errors follow RFC 9457 (Problem Details):

```json
{
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "Proceso no encontrado.",
  "instance": "/api/v1/procesos/99"
}
```

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

## Architecture

### Technology stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Validation, Data JPA, Security 7) |
| Persistence | Hibernate 7.4 · Flyway 12 · H2 (`dev` and tests) · PostgreSQL (`prod`) |
| Security | Spring Security `AuthenticationManager` · JWT (jjwt 0.12.6, HS256) · BCrypt · SHA-256-hashed refresh tokens |
| API documentation | springdoc-openapi 3 (OpenAPI 3 and Swagger UI) |
| Web app | Angular 19 · Bootstrap 5 · RxJS |
| Testing | JUnit 5 · Mockito · MockMvc · AssertJ · ArchUnit 1.4 · JaCoCo |
| Tooling | Maven Wrapper · Lombok · MapStruct · Docker · GitHub Actions · SonarCloud |

### Modules

The code is split into two business modules and two shared packages. Each business module is layered as
controller → service interface → service implementation → repository → model, with `dto` for the module's contract
and `mapper` for the MapStruct translations.

| Package | Responsibility |
|---|---|
| `security` | Filter chain, login and its rate limit, JWT issuing and validation, closed sessions, `ApiPrincipal`, `401` and `403` handlers, CORS |
| `common` | Tenant base entity, tenant-aware repository contract, business exceptions, Problem Details, pagination |
| `gestion` | Management: stores, users and their sessions, processes, process roles and change history |
| `modelado` | BPMN modeling: pools, lanes, activities, gateways, sequence flows, message flows and correlation keys |

### Request lifecycle

1. The JWT filter validates the token and builds an `ApiPrincipal` (`usuarioId`, `empresaId`, role and session) from
   its claims, without a database query. A token whose session was closed is rejected.
2. The role rules decide `401` or `403` before any controller runs.
3. Controllers receive the principal with `@AuthenticationPrincipal` and pass `empresaId` explicitly to the services.
4. Every lookup by id goes through `findByIdAndEmpresaId`, so a resource from another store does not exist for the
   caller.
5. The service maps the result to a DTO inside its transaction. Entities never reach the controller.

### Module boundaries

`modelado` builds on the processes and roles of `gestion`, so `gestion` never depends on `modelado`. When a process
is created, `gestion` publishes a `ProcesoCreado` event and `modelado` creates the store's pool in the same
transaction. When a process is deleted, a `ProcesoEliminado` event lets `modelado` retire the model. To know whether
a process role is in use, `gestion` asks the `UsoDeRoles` port, which `modelado` implements on top of its lanes.

## Quality and testing

```bash
./mvnw verify
```

The build runs 392 tests and a JaCoCo coverage check. The HTML report is written to `target/site/jacoco/index.html`.

| Suite | Tests | Scope |
|---|---:|---|
| Architecture (ArchUnit) | 30 | Layering, module boundaries and package cycles, DTOs and mappers, tenant isolation, JPA mapping (inheritance, enums, lazy associations), no `HttpSession`, and a declared profile in every `@SpringBootTest` |
| Controller slices (`@WebMvcTest`) | 114 | Routes, status codes, JSON shape and validation, with the real security rules |
| Service unit tests (Mockito) | 31 | Business rules of the management module |
| Security and isolation (`@SpringBootTest`) | 154 | The two-store IDOR suite, read-only sharing (HU-23), the role matrix, JWT tampering and expiry, sessions, the login limit, idempotency keys, the last active administrator under concurrent changes, and end-to-end `401`, `403`, `429` and firewall `400` responses |
| Profiles, schema, queries, API contract and demo data (`@SpringBootTest`) | 23 | What `dev` and `prod` expose, the Flyway migrations and unique indexes, SQL statement counts that catch N+1 queries and prove that the JWT filter runs no SQL, the OpenAPI contract, and the demo data read through the API |
| Module integration (`@SpringBootTest`) | 38 | Process-role usage across modules, the order of pools and lanes, the whole diagram, optimistic locking on every edit, auditing, soft delete, the modeling history and the BPMN consistency rules |
| Application context | 2 | The full context starts in the `test` profile, without the demo store |

Current coverage: 95 % of lines and 75 % of branches.

Every push to `main` and every pull request runs the GitHub Actions pipeline:

| Job | What it checks |
|---|---|
| Build & Test | `./mvnw verify` on Ubuntu and Windows. The test results appear as a check, and the coverage report is kept as an artifact. |
| Architecture Rules | The ArchUnit suite on its own, with a summary |
| Docker Image | Builds the image, checks that the API answers from the container, and starts it in the `prod` profile against PostgreSQL 16 |
| SonarCloud Analysis | Static analysis, skipped when SonarCloud is not configured |
| Frontend Build | `npm ci` and a production build of the web app |

## Design decisions

- **`404` instead of `403` across stores.** Answering "forbidden" would confirm that another store's resource exists.
- **The tenant comes only from the token.** Request DTOs cannot carry an `empresaId`, and ArchUnit enforces it.
- **Claims instead of a query per request.** The filter trusts the signed claims, so authenticating a request runs no
  SQL. The one thing a signed token cannot know, that its session was closed, comes from an in-memory list filled by
  the logout, a reused refresh token, a deactivation or a role change. Access tokens last 15 minutes, so the list only
  needs to remember a session that long.
- **Refresh tokens rotate and work once.** A stolen refresh token either fails, because its owner already used it, or
  closes the session as soon as the owner uses theirs. The database keeps SHA-256 hashes: the tokens are already
  random, so BCrypt adds nothing, and the hash has to be searchable.
- **The version travels in the body.** A single-page app edits a resource through a form, so sending the `version`
  back with the other fields is simpler than the `ETag` and `If-Match` headers of HTTP. The API still refuses an
  edit without it.
- **A row lock guards the last administrator.** Two administrators who remove each other's role change different
  rows, so optimistic locking alone would let both changes through. Locking the store's row makes the second change
  wait and count again.
- **Single-table inheritance for flow nodes.** Activities and gateways share one table and one identity, so sequence
  flows can point to either of them.
- **Soft delete everywhere.** Processes and process roles carry their own `activo` flag. BPMN elements use Hibernate's
  `@SQLDelete` and `@SQLRestriction`, so a delete becomes an update and no query sees retired rows. Hibernate's
  `@SoftDelete` would have forced eager to-one associations, against the project's lazy-loading rule. A unique
  constraint that a retired row would still hold, such as the pair of nodes of a sequence flow, only counts active
  rows.
- **One error format.** Validation, business and security errors all return Problem Details, so clients handle a
  single shape.
- **Unknown fields are errors.** Jackson fails on properties that the contract does not define, so a typo or a
  smuggled `empresaId` gets a `400` instead of being silently dropped.
- **The demo data goes through the services.** The seed cannot create a diagram that the API itself would reject.
- **Services return DTOs.** MapStruct maps inside the service transaction, so `open-in-view` stays off and no lazy
  association is read after the session closes. Controllers depend on service interfaces, never on their
  implementations.
- **Lazy associations, explicit fetching.** Every association is `LAZY`. A list that shows associated data fetches it
  with an `@EntityGraph`, and a test counts SQL statements so that an N+1 query fails the build.
- **A one-way dependency between modules.** Events and a port let `modelado` react to and answer `gestion` without
  `gestion` knowing `modelado`, so the modules can grow without a cycle.
- **The schema belongs to Flyway.** Migrations are the single source of truth, and Hibernate only validates them
  (`ddl-auto=validate`). Portable SQL lives in `db/migration/common`. What only one engine can express, such as
  PostgreSQL's partial unique indexes, lives in `db/migration/{vendor}`, and H2 gets an equivalent built on a
  generated column.
- **Every text column has a length, and so does its request field.** A value that is too long answers `400` before it
  reaches the database. Passwords stop at 72 characters, because BCrypt only reads 72 bytes and Spring Security
  rejects longer ones.
- **Tests never touch the development database.** Every `@SpringBootTest` declares its profile, which an ArchUnit
  rule checks, and the `test` profile gives each Spring context its own in-memory database.

## Roadmap

**Peak-traffic readiness**
- [ ] k6 load tests that simulate a sales peak, with thresholds in CI
- [ ] Connection-pool and thread-pool sizing based on those measurements
- [ ] Second-level cache for published processes, which are read often and change rarely
- [x] `Pageable`-based pagination with stable sorting for every collection that can grow

**Consistency under concurrency**
- [x] Optimistic locking with `@Version`, so two editors cannot overwrite each other (`409 Conflict`)
- [x] Idempotency keys on create requests, so a retried call does not duplicate a process
- [ ] Process versioning: editing a published process opens a new draft version

**Security**
- [x] Globally unique user emails, so a new store cannot reuse an existing user's login email
- [x] Read-only process sharing between stores (HU-23), through a read door that no change can use
- [x] Authentication through `AuthenticationManager` and `UserDetailsService`, without revealing whether an email exists
- [x] Short-lived access tokens with refresh tokens
- [x] Rate limiting on login (`429` with `Retry-After`)
- [ ] Scheduled purge of expired sessions, refresh tokens and old idempotency keys

**Data and auditability**
- [x] Flyway migrations with `ddl-auto=validate`, composite unique constraints and `empresa_id` indexes
- [x] Soft delete and change history for every BPMN element
- [x] Auditing fields (`createdBy`, `lastModifiedBy`) filled from the authenticated principal
- [x] Lazy associations with entity graphs and read-only transactions

**API contract**
- [x] Full OpenAPI documentation (`@Tag`, `@Operation`, `@ApiResponse`, `@Schema`), not published in production
- [x] Field-level validation errors in Problem Details
- [x] Aggregate endpoint that returns a complete BPMN diagram for the back-office web app

**Architecture and quality**
- [x] Request and response DTO packages with MapStruct mappers, and services exposed as interfaces
- [x] Module boundaries between `gestion` and `modelado` enforced by ArchUnit, with no dependency cycles
- [x] Complete Spring profiles: `dev` with seed data, `test` with an isolated in-memory database, and `prod`
- [ ] Repository tests with `@DataJpaTest` and unit tests for every modeling service
- [ ] Coverage gate per package (services at 70 % or more, branches included) and a SonarCloud quality gate
- [ ] Docker Compose with PostgreSQL, Actuator health checks and Testcontainers-based integration tests

## Credits

This project began as a five-person team project for the Web Development course of my Systems Engineering degree. It
was built in the team repository [Facimus-Curiositatem/Beta-back](https://github.com/Facimus-Curiositatem/Beta-back),
and the first commit here is a snapshot of that repository.

My contributions to the team version:

- **Stateless security:** the Spring Security filter chain, JWT issuing and validation, `ApiPrincipal`, Problem Details
  responses for `401` and `403`, and CORS.
- **Multi-tenancy and authorization:** tenant checks on every lookup, including listings by parent resource and ids
  received in request bodies; the cross-tenant `404` policy that prevents IDOR; the centralized role matrix; the
  ArchUnit isolation rules; and the two-store integration suite.

This repository is my personal continuation of the project. It evolves independently from the team version, is now
oriented to e-commerce operations, and follows the roadmap above.

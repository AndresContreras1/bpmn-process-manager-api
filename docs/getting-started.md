[← Documentation](README.md)

# Getting started

## Requirements

- JDK 21, or Docker, for the API
- Node.js 22 or later for the web app (optional)
- Docker for the tests that run against a real PostgreSQL (optional)

## Run the API

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

## First requests

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

## Run the web app

```bash
cd frontend
npm ci
npm start
```

The app opens on `http://localhost:4200`, and the Angular dev server forwards the `/api` calls to the API on port
8080. [frontend/README.md](../frontend/README.md) describes its structure and conventions.

## The whole stack with one command

Database, API and web app together, each in its own container:

```bash
cp .env.example .env   # fill in DB_PASSWORD and JWT_SECRET
docker compose up -d --build --wait
```

`--wait` comes back when all three report healthy, and the app is then on `http://localhost` (or on `WEB_PORT`).

**The stack starts empty.** It runs the `prod` profile, and the demo store belongs to `dev`, so there is nothing to
sign in with yet: create your store from the app, or with `POST /api/v1/empresas`, and the account you give it is
its first administrator. To poke at the demo data instead, run the API with `dev` as described above.

The browser only ever talks to the web container: NGINX serves the compiled app and forwards everything under
`/api` to the API inside the network. So the production build has no absolute API URL in it, there is no CORS to
configure, and the same image runs anywhere without being rebuilt. The API keeps its own port for the Postman
collection and for curl, but nothing in the browser uses it.

The database keeps its data in a named volume, so `docker compose down` does not lose it; `docker compose down -v`
does. The API waits for the database to answer and reports itself as up only once Flyway has migrated the schema,
which `--wait` and the container health checks rely on. `.env` is not committed, and the two values without a
default, `DB_PASSWORD` and `JWT_SECRET`, stop the stack until they are set.

## End-to-end tests

```bash
docker compose up -d --build --wait
./mvnw -B -f e2e/pom.xml test
```

A separate Maven module that does not hang from this pom, so `./mvnw verify` never runs it and the normal build
stays as fast as it was. It drives a headless Chrome against the stack above: signing in, creating a process,
fixing a diagram in the editor until the diagnosis lets it be published, sharing it and reading it as the other
store, and administering roles and users. [e2e/README.md](../e2e/README.md) says what each scenario covers.

## Postman collection

The [Postman collection](../postman/) covers a second scenario, in which *Acme Store* models how it hands orders over to
a third-party logistics (3PL) partner. Run the requests in order, one by one or with the Collection Runner. The last
folder deletes what the scenario created, children first.

The scenario tours the endpoints rather than finishing a model, so its diagram stays incomplete on purpose:
publishing it answers `409` with what the diagnosis found, which is the rule at work. To see a published process,
use the demo store of the `dev` profile, where *Order fulfillment* starts published as version 1.

The *Operacion simulada* folder is the one part that needs a finished model: a case runs on a published version, so
those requests answer `409` until the diagram passes the diagnosis and is published, and they need a plain start
event, because a process that starts with a message is opened by sending that message. It is there to document the
shape of every request of the operation.

## Docker

```bash
docker build -t bpmn-process-manager-api .
docker run -p 8080:8080 \
  -e JWT_SECRET=<at-least-32-random-characters> \
  -e SPRING_DATASOURCE_URL=jdbc:h2:mem:procesos \
  bpmn-process-manager-api
```

The image is a multi-stage build that runs as a non-root user. This command starts the `dev` profile on an in-memory
database with Demo Store. For persistent data, use the `prod` profile with PostgreSQL.

## Operations endpoints

| Endpoint | Who can call it | What it answers |
|---|---|---|
| `GET /actuator/health` | Anyone | `UP` or `DOWN`, with no detail of what runs behind it |
| `GET /actuator/health/liveness` · `/readiness` | Anyone | The probes a container or an orchestrator polls; readiness covers the database |
| `GET /actuator/info` | Anyone | The name and version of the running build |
| `GET /actuator/metrics` | Administrator | JVM, pool and HTTP metrics one by one, the four gauges of the operation, and the hits and misses of the published-version cache |

Nothing else is exposed: any other Actuator endpoint answers `404`.

[← Documentation](README.md)

# Runbook

What to look at, and what to do, when the running product misbehaves. Today a deployment is the Compose stack of
this repository (`compose.yaml`); the remote environments add their own steps to this page when they arrive.

## The stack

| Service | What it is | Ports | Health check |
|---|---|---|---|
| `db` | PostgreSQL 16, data in the `postgres-data` volume | `5432`, not published | `pg_isready` |
| `api` | The API in the `prod` profile | `8080` for the API (`API_PORT` on the host, only on `127.0.0.1`); `8081` for Actuator, never published | `/actuator/health/readiness` on `8081` |
| `web` | NGINX with the compiled app, and the door to the API under `/api` | `8080` (`WEB_PORT` on the host) | `/healthz` |

The API and the web containers run read-only, without Linux capabilities, and write only to `/tmp`, which lives in
memory. `docker compose ps` shows whether each one is healthy.

```bash
docker compose ps
docker compose exec api wget -q -O - http://127.0.0.1:8081/actuator/health/readiness
curl -s http://localhost:${WEB_PORT:-80}/healthz
```

## Following one failed request

Every response carries `X-Request-Id`, and every error carries the same `requestId` and a `traceId` in its body. Ask
whoever reports the problem for one of them.

1. Find the lines of that request: in `prod` each log line is a JSON object with its `requestId` and `traceId`, and an
   error from outside a controller logs its stack trace in that line.

   ```bash
   docker compose logs api | grep '"requestId":"<the id>"'
   ```

2. If the spans are being exported (`MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT`), search the `traceId` in
   the collector to see where the time went: the request, the engine (`avanzar el caso`) and the diagnosis.
3. A `500` never tells the client what failed; the log line does. A `4xx` says what was wrong in its `detail`.

## What to watch

Prometheus reads `http://api:8081/actuator/prometheus` from inside the network. The series that tell the most:

| Series | What a change means |
|---|---|
| `http_server_requests_seconds` by `status` and `uri` | More `5xx`, or a slower p95, after a deployment |
| `hikaricp_connections_pending` | Requests waiting for a connection; after three seconds they fail |
| `login_fallidos_total` by `motivo` | `credenciales` climbing is someone guessing; `bloqueado` is the limit doing its work |
| `casos_eventos_total` by `tipo` | Cases stuck: many `error` lines, or `abierto` without `finalizado` |
| `procesos_motor_avanzar_seconds` by `estado` | The engine getting slower, or more cases ending in `error` |
| `jvm_memory_used_bytes`, `process_cpu_usage` | The container running out of room |

## Common incidents

**The API is not ready.** Readiness covers the database, so `DOWN` almost always means PostgreSQL: look at
`docker compose logs db`, at the disk of the host and at the credentials in `.env`. The API comes back by itself when
the database does.

**More `5xx` than usual.** Take two or three `requestId` values and follow them as above. If the errors started with
a deployment, go back to the previous build of the images and redeploy it while the cause is found.

**Requests time out waiting for the database.** `hikaricp_connections_pending` above zero for minutes means the pool
(`DB_POOL_SIZE`, 10 by default) is not enough for the load or a query got slow. Every request runs on a virtual
thread, so the pool is the real ceiling: raise it only if PostgreSQL has room for more connections.

**Many `429` on login.** The limit counts failed logins per email and address (`LOGIN_MAX_FAILED_ATTEMPTS` in
`LOGIN_FAILED_ATTEMPTS_WINDOW`), in the table `intentos_login` that every instance shares. A person locked out
waits for the window to pass, or an operator forgets their failures with
`delete from intentos_login where clave like 'ana@acme.com|%'`. An attack shows as
`login_fallidos_total{motivo="credenciales"}` climbing for many emails.

**A closed session still works in another instance.** Each instance hears of a closure through PostgreSQL. If the
log says "Se corto la escucha de las sesiones cerradas", the connection that listens dropped: it reopens every five
seconds and rereads the recent closures when it does, so the session stops working within that time.

**A scheduled job did not run.** `select * from shedlock` shows who holds each lock and until when. A lock taken
by an instance that died frees itself when `lock_until` passes: thirty minutes for the purge, ten for the rest.

**A job of the queue failed.** A job that ran out of attempts stays in `trabajos` as `FALLIDO`, with its last error,
for a week. Once the cause is fixed, give it one more attempt; a handler can run the same job twice without doing
it twice, so trying again is safe.

```sql
select id, tipo, empresa_id, intentos, ultimo_error, terminado_en from trabajos
where estado = 'FALLIDO' order by terminado_en desc;

update trabajos set estado = 'PENDIENTE', maximo_intentos = intentos + 1, disponible_desde = now(),
                    terminado_en = null
where id = 42;
```

**Jobs pile up.** `select estado, count(*) from trabajos group by estado` shows the queue. Many `PENDIENTE` whose
`disponible_desde` already passed means the workers do not keep up: raise `TRABAJOS_TRABAJADORES`, if the pool has
room, or add an instance. A job `EN_CURSO` for more than fifteen minutes was left by an instance that died, and the
queue takes it back by itself.

**An event did not reach its listener.** `select listener_id, event_type, publication_date from event_publication`
lists what a listener did not finish; the log line of its failure says why. The next start of an instance
delivers it again, and a listener can take the same event twice.

**The JWT signing key leaked.** Generate a new `JWT_SECRET` of at least 32 bytes, put it in `.env`, leave
`JWT_PREVIOUS_SECRET` empty and recreate the API with `docker compose up -d api`. Every access token signed with the
old key stops working, so every person renews or signs in again; that is the point.

**The database password leaked.** Change it in PostgreSQL (`ALTER ROLE procesos PASSWORD '...'`), then in `.env`, and
recreate the API.

**A security alert.** Dependabot, CodeQL, Trivy and gitleaks report in the Security tab of the repository. A critical
vulnerability with a fix already fails the pipeline; a high one is fixed in the next pull request. A leaked secret is
rotated first and removed from the history after: removing it does not make it secret again.

**A screen of the web app is blank or without styles.** The browser's console says what the CSP blocked. The
hashes of its scripts come from the build of the image, so an index changed by hand after the build no longer
matches them: rebuild the image with `docker compose build web` instead of patching the container.

**The AI review answers `503`.** `GEMINI_API_KEY` is missing or the model does not answer in `GEMINI_TIMEOUT`. Nothing
else depends on it; removing the key turns the review off on purpose.

## Backups

Until the remote environments bring daily encrypted copies, a copy is taken by hand, from the host:

```bash
docker compose exec -T db pg_dump -U procesos -Fc procesos > procesos-$(date +%F).dump
```

Restoring replaces what the database has, so stop the API first:

```bash
docker compose stop api
docker compose exec -T db pg_restore -U procesos -d procesos --clean --if-exists < procesos-2026-10-05.dump
docker compose start api
```

A copy that was never restored is a hope, not a backup: restore one into a scratch stack now and then.

## Stopping and starting

`docker compose stop api` lets the requests in progress finish, for up to `SHUTDOWN_TIMEOUT` (20 seconds); Compose
kills the container 30 seconds after asking. `docker compose down` keeps the database volume; `down -v` deletes it.

## Routine work

| When | What |
|---|---|
| Every Monday | Read the Dependabot pull requests: each one runs the whole pipeline, and a green one can be merged |
| Every September | Renew `Expires` in `frontend/public/.well-known/security.txt`; the pipeline starts failing a month before it passes |
| When a person leaves | Deactivate their user: their sessions close at once |
| Rotating the signing key | Move the key in use to `JWT_PREVIOUS_SECRET`, put a new one in `JWT_SECRET` and recreate the API: nobody is signed out. Fifteen minutes later, when no token of the old key is still alive, empty `JWT_PREVIOUS_SECRET` and recreate it again |
| Before a new major of PostgreSQL | Take a backup and restore it into the new version; Dependabot does not offer majors of the images |
| A month after production runs on HTTPS | If every subdomain is on HTTPS, add `preload` to `Strict-Transport-Security` in `frontend/cabeceras.conf` and submit the domain at hstspreload.org. Leaving that list takes months |

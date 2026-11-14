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
| `almacen` | SeaweedFS, the S3 storage of the documents and of the audit-trail anchors, data in the `almacen-data` volume | `8333`, not published | `/healthz` on `8333` |

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
| `hikaricp_connections_usage_seconds` | How long the code keeps a connection; a long tail is a slow query or a transaction that waits on something else |
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

**A request fails with "canceling statement due to statement timeout".** A statement of the API ran longer than
`DB_STATEMENT_TIMEOUT`, 30 seconds unless changed, and PostgreSQL cancelled it; the request answers `500` and the
connection goes on serving. Find the statement in `pg_stat_statements` (below) and look at its plan with
`EXPLAIN (ANALYZE, BUFFERS)` from `psql`: a missing index or a lock held by another transaction are the usual causes.
Raise the limit only for a statement that has to be long, never to hide a slow one.

**The log says "terminating connection due to idle-in-transaction timeout".** A transaction stayed open for more than
`DB_IDLE_IN_TRANSACTION_TIMEOUT` doing nothing, usually while the code waited for something else, and PostgreSQL
closed it so it would not keep its locks. The work of that transaction is rolled back. The stack of the request in the
log says which code it was: nothing slow belongs inside a transaction.

**The log says "Connection leak detection triggered".** A connection stayed out of the pool longer than
`DB_LEAK_DETECTION_THRESHOLD`, 20 seconds unless changed. The warning carries the stack of the code that took it; if
"Previously reported leaked connection ... was returned to the pool" follows, it was only slow, and if nothing follows,
it leaked and the pool has one connection less until the instance restarts.

**Many `429` on login.** The limit counts failed logins per email and address (`LOGIN_MAX_FAILED_ATTEMPTS` in
`LOGIN_FAILED_ATTEMPTS_WINDOW`), in the table `intentos_login` that every instance shares. A person locked out
waits for the window to pass, or an operator forgets their failures with
`delete from intentos_login where clave like 'ana@acme.com|%'`. An attack shows as
`login_fallidos_total{motivo="credenciales"}` climbing for many emails.

**People are signed out sooner than they expect.** A session ends after the store's idle timeout without being
renewed, and the store's maximum duration after its login, an hour and a day unless the store shortened them
(`select inactividad_sesion_minutos, duracion_sesion_horas from configuracion_tienda where empresa_id = 7`). A tab
left open does not renew by itself, so it signs in again; that is the limit working.

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

**The key of the second factor leaked.** `MFA_CLAVE_DE_CIFRADO` decrypts the TOTP secrets of the database and
signs the recovery codes, so with a copy of the database it opens the second factors. Put a new one in `.env` and
recreate the API, then delete what the old key protected, which no longer decrypts:
`DELETE FROM codigos_de_recuperacion; DELETE FROM segundos_factores;`. Every person signs in with the password alone
until they enroll their app again; in a store that asks for it, they can only enroll it.

**A person lost the phone and the recovery codes.** An administrator of their store removes their second factor
(`DELETE /api/v1/usuarios/{id}/segundo-factor`, after confirming their own identity), which closes that person's
sessions. They sign in with the password and enroll a new app.

**A query answers no rows in production.** Row-level security shows a connection only the rows of the store of
its thread. Code that runs outside a request and does not say its scope sees nothing: wrap it in
`ContextoDeTienda.comoSistema` if it crosses stores on purpose, or `deLaTienda` if it works for one. To look at the
data by hand, `psql` connects as the owner, which is not limited; `SET ROLE procesos_app;` and
`SELECT set_config('app.empresa_id', '<id>', false);` show what the API would see.

**The audit trail does not verify.** `GET /api/v1/auditoria/verificacion` names the first entry that no longer
matches. Nothing in the API can change it, so it was changed in the database with the trigger off: treat it as an
incident. Keep a copy of the table as it is, and compare it with the last backup to find what changed.

**A document stays `PENDIENTE`.** Its review is a job of the queue (`documentos.revisar`). Without
`ANTIVIRUS_HOST` and with `DOCUMENTOS_EXIGIR_ANTIVIRUS=true`, it waits on purpose: its `motivo` says so. With an
antivirus, look at the job: `select estado, intentos, ultimo_error from trabajos where tipo = 'documentos.revisar'
order by id desc limit 5`. "ClamAV no contesto" means clamd is down or still loading its signatures, which takes a
minute after it starts; the job tries again by itself.

**A document was blocked and the person says it is clean.** The `motivo` says who blocked it: the name of the
signature ClamAV found, or the limits of a container (it grows too much when opened, has too many parts, or a path
that leaves it). A false positive of the antivirus is not overridden from the API: the person uploads the file again
as a PDF or another type, or the operator updates the signatures (`freshclam`).

**The audit trail does not match its anchor.** The chain closes by itself but not with the hash the anchor kept that
day: the table was rewritten after that day. Treat it as an incident: keep the database as it is, compare the
anchored hash (`anclas/<store>/<day>` in the anchors' bucket) with the entry of that sequence, and restore from the
backup of before that day to see what changed. An anchor that "is no longer in the storage" means the bucket was not
created with Object Lock, or its retention passed.

**An export stays `PENDIENTE` or ends `FALLIDA`.** It is a job of the queue (`exportaciones.generar`): `select
estado, intentos, ultimo_error from trabajos where tipo = 'exportaciones.generar' order by id desc limit 5`. A failed
export's `motivo` only says it failed; the cause is in the log, `No se pudo armar la exportacion <id> de la tienda
<store>`, usually a file the storage could not give back. The administrator asks for another one. One that stays pending more than `EXPORTACIONES_PENDIENTE_MAXIMA`
(six hours) is marked failed by the hourly cleanup, so the store is never stuck with one in course. The e-mail with
the link is another job (`exportaciones.correo`), and an expired export sends nothing.

**A former customer asks for the deletion certificate.** Their store is gone and nobody can sign in to it. Read
`certificados/<store id>/borrado` from the anchors' bucket (or the storage folder without S3), and check that its
SHA-256 is the one the store's audit trail kept: `select detalle from bitacora where empresa_id = <store id> and
accion = 'TIENDA_BORRADA'`. Send them the file; it names nobody
([Export format](export-format.md#the-deletion-certificate)).

**Someone gets `429` while working.** The rate limits count changes per person and signing in per address
([Rate limiting](security.md#rate-limiting)). An office behind one address shares the bucket of signing in: raise
`LIMITE_AUTENTICACION_POR_MINUTO` in `.env` and recreate the API. A bucket fills again on its own; to empty one at
once, `DELETE FROM cubos_de_peticiones WHERE id LIKE 'autenticacion:ip:<address>'`.

**The database password leaked.** Change it in PostgreSQL (`ALTER ROLE procesos PASSWORD '...'`), then in `.env`, and
recreate the API.

**A security alert.** Dependabot, CodeQL, Trivy and gitleaks report in the Security tab of the repository. A critical
vulnerability with a fix already fails the pipeline; a high one is fixed in the next pull request. A leaked secret is
rotated first and removed from the history after: removing it does not make it secret again.

**A screen of the web app is blank or without styles.** The browser's console says what the CSP blocked. The
hashes of its scripts come from the build of the image, so an index changed by hand after the build no longer
matches them: rebuild the image with `docker compose build web` instead of patching the container.

**An e-mail did not arrive.** The e-mails leave through the queue: `select id, estado, intentos, ultimo_error from
trabajos where tipo = 'correo-de-cuenta' order by creado_en desc` says whether it left, is waiting for its next
attempt or failed, and why. "No hay servidor de correo configurado" means `SMTP_HOST` is empty. If it left and
never arrived, the receiving server probably took it for spam: check the SPF, DKIM and DMARC of the sender's
domain.

**The AI review answers `503`.** `GEMINI_API_KEY` is missing or the model does not answer in `GEMINI_TIMEOUT`. Nothing
else depends on it; removing the key turns the review off on purpose.

## Which query got slow

PostgreSQL counts, for every statement, how often it ran, how long it took and how many rows it touched, in
`pg_stat_statements`. The Compose stack loads it when the database starts and the migrations create the extension; a
managed database needs `shared_preload_libraries = 'pg_stat_statements'` in its settings and a superuser to create it.

```sql
-- What took the most time since the last reset
select calls, round(total_exec_time) as total_ms, round(mean_exec_time::numeric, 2) as mean_ms, rows,
       left(query, 120) as query
from pg_stat_statements
where dbid = (select oid from pg_database where datname = current_database())
order by total_exec_time desc limit 15;

-- Start counting again, for example after a deployment
select pg_stat_statements_reset();
```

A statement with a high `mean_ms` is slow by itself: its `EXPLAIN (ANALYZE, BUFFERS)` says why. One with a low
`mean_ms` and a huge `calls` runs once per row of something: look for a loop in the code.

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

The documents live in the `almacen-data` volume (SeaweedFS), apart from the database: a copy of one without the other
leaves rows without files or files without rows. Copy both at the same time, or stop the API while copying.

## Stopping and starting

`docker compose stop api` lets the requests in progress finish, for up to `SHUTDOWN_TIMEOUT` (20 seconds); Compose
kills the container 30 seconds after asking. `docker compose down` keeps the database volume; `down -v` deletes it.

## Routine work

| When | What |
|---|---|
| Every Monday | Read the Dependabot pull requests: each one runs the whole pipeline, and a green one can be merged |
| Every September | Renew `Expires` in `frontend/public/.well-known/security.txt`; the pipeline starts failing a month before it passes |
| When a person leaves | Deactivate their user: their sessions close at once. If they ask for their data to be deleted, anonymize them instead (`POST /api/v1/usuarios/{id}/anonimizar`), which cannot be undone |
| When a store asks to leave | Its administrator exports everything and closes it (`POST /api/v1/empresas/actual/baja` with `"exportar": true`): for 30 days it can still read everything, export again and cancel. The purge of the night after deletes it, the log says `Tienda N borrada al cumplir la gracia de su baja`, and its deletion certificate is in the anchors' bucket |
| Raising the cost of BCrypt | Set `CLAVES_COSTO_BCRYPT` higher and recreate the API: each login rehashes its password, and nobody has to change it. Each step doubles what a login costs, so measure one first |
| Before the first e-mail in production | Publish in the DNS of the domain of `MAIL_FROM` the SPF and DKIM records the provider gives, and a DMARC policy (`p=quarantine` to begin with). Without them most receivers take the e-mails for spam |
| Rotating the signing key | Move the key in use to `JWT_PREVIOUS_SECRET`, put a new one in `JWT_SECRET` and recreate the API: nobody is signed out. Fifteen minutes later, when no token of the old key is still alive, empty `JWT_PREVIOUS_SECRET` and recreate it again |
| Before a new major of PostgreSQL | Take a backup and restore it into the new version; Dependabot does not offer majors of the images |
| A month after production runs on HTTPS | If every subdomain is on HTTPS, add `preload` to `Strict-Transport-Security` in `frontend/cabeceras.conf` and submit the domain at hstspreload.org. Leaving that list takes months |

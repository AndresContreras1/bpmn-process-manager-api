[← Documentation](README.md)

# Configuration

## Profiles

| Profile | Activated by | Database | Demo Store | OpenAPI and Swagger UI | SQL log |
|---|---|---|:---:|:---:|:---:|
| `dev` | Default, when no profile is set | PostgreSQL 16 from `compose.dev.yaml`, which Spring Boot starts | ✓ | ✓ | ✓ |
| `test` | `@ActiveProfiles("test")` in integration tests | Its own database in the PostgreSQL 16 of the test run | — | ✓ | — |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | PostgreSQL, from the variables below | — | — | — |

In `dev`, Spring Boot's Docker Compose support starts the database of `compose.dev.yaml` with the application and
leaves it running when the application stops, with its data in a volume. Without Docker, set
`spring.docker.compose.enabled=false` and point the `DB_*` variables at a PostgreSQL of your own. The tests leave
the profile's database aside: [The database of the tests](testing.md#the-database-of-the-tests) says how.

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` uses PostgreSQL and leaves out the demo store and the API documentation | `dev` |
| `DB_HOST` · `DB_PORT` · `DB_NAME` | Database location, in `prod` and in `dev` without Docker | `localhost` · `5432` · `procesos` |
| `DB_USER` · `DB_PASSWORD` | Database credentials | `procesos` · empty |
| `JWT_SECRET` | HS256 signing key of at least 32 bytes. The `prod` profile does not start without it. | None |
| `MFA_CLAVE_DE_CIFRADO` | Key of at least 32 characters from which the API derives the AES-GCM key of the TOTP secrets and the HMAC key of the recovery codes. `dev` and `test` have a fixed one, and `prod` does not start without it. It does not rotate: changing it voids every enrolled second factor | None in `prod` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | The OAuth client of Google. Without the id, Google is not offered. Its redirect URI is `<API>/api/v1/auth/oidc/callback/google` | Empty |
| `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET` | The app registration of Microsoft Entra ID. Without the id, Microsoft is not offered. Its redirect URI is `<API>/api/v1/auth/oidc/callback/microsoft`, and it has to emit the optional claims `email` and `xms_edov` | Empty |
| `WEBAUTHN_RP_ID` | The domain of the web app, without scheme or port: a passkey only works on the site it was created for and its subdomains | `localhost` |
| `WEBAUTHN_ORIGINS` | The exact addresses the browser signs passkeys from, separated by commas; the web app's address in production | `APP_PUBLIC_URL` |
| `WEBAUTHN_RP_NAME` | The name the device shows when it creates a passkey | `BPMN Process Manager` |
| `MICROSOFT_TENANT` | `organizations` for any work or school account, or the id of one organization to accept only it | `organizations` |
| `ALMACEN_S3_BUCKET` | The S3 bucket of the documents. Without it they go to `ALMACEN_DIRECTORIO` on disk, which suits development and a single instance | Empty |
| `ALMACEN_S3_BUCKET_ANCLAS` | A bucket created with Object Lock, for the daily anchor of the audit trail. Without it the anchor goes to the documents' bucket, unlocked | Empty |
| `ALMACEN_S3_ENDPOINT` | The address of an S3-compatible service, such as the SeaweedFS of `compose.yaml`; empty for AWS | Empty |
| `ALMACEN_S3_REGION` · `ALMACEN_S3_USUARIO` · `ALMACEN_S3_CLAVE` | Region and credentials; without a user, the AWS default chain (environment, profile, role) | `us-east-1` · empty |
| `ALMACEN_S3_CREAR_BUCKETS` | Create the missing buckets when starting, the anchors' one with Object Lock. For `compose.yaml`; in AWS they are created beforehand | `false` |
| `ALMACEN_S3_CIFRAR` | Ask the service to encrypt every document (SSE-S3) | `false` |
| `ALMACEN_DIRECTORIO` | The folder of the disk storage | `<tmp>/bpmn-process-manager/almacen` |
| `ANTIVIRUS_HOST` · `ANTIVIRUS_PUERTO` | Where clamd listens. Without a host, documents are not reviewed and say so | Empty · `3310` |
| `DOCUMENTOS_EXIGIR_ANTIVIRUS` | Without an antivirus, keep documents pending instead of making them available unreviewed | `false` |
| `DOCUMENTOS_TAMANO_MAXIMO` · `DOCUMENTOS_CUOTA_POR_TIENDA` | Largest document, and space per store counting every version | `25MB` · `1GB` |
| `BITACORA_ANCLA_RETENCION` | How long nobody can delete an anchor of the audit trail | `400d` |
| `JWT_PREVIOUS_SECRET` | The signing key before the last rotation: its tokens are still accepted, and nobody signs with it. Empty outside a rotation | Empty |
| `JWT_EXPIRATION_SECONDS` | Access token lifetime. Keep it under 30 minutes, the shortest idle timeout a store can choose: the refresh token and the session last what each store decides in its settings | `900` |
| `CLAVES_COSTO_BCRYPT` | Cost of BCrypt, from 4 to 31: each step doubles the time of a hash. Raising it invalidates no password, because each login rehashes with the new one | `10` |
| `LIMITE_AUTENTICACION_POR_MINUTO` · `LIMITE_REGISTRO_POR_HORA` · `LIMITE_CORREO_POR_HORA` · `LIMITE_ESCRITURA_POR_MINUTO` | The [rate limits](security.md#rate-limiting): signing in, registering and e-mail per address, every other change per person | `30` · `10` · `20` · `600` |
| `LIMITES_ACTIVOS` | `false` turns every rate limit off | `true` |
| `LIMITE_CUERPO` | The largest body a request can bring; more answers `413` | `1MB` |
| `TURNSTILE_SECRET` | The secret of Cloudflare Turnstile. Set, registering a store asks for its verification | Empty |
| `LOGIN_MAX_FAILED_ATTEMPTS` · `LOGIN_FAILED_ATTEMPTS_WINDOW` | Failed logins for an email from one address before `429`, and the window that counts them | `5` · `15m` |
| `CORS_ALLOWED_ORIGINS` | Allowed storefront or back-office origins | `http://localhost:4200` |
| `LIMPIEZA_CRON` | When the nightly purge runs; it also deletes the stores whose closing is 30 days old. `-` turns it off. | `0 30 3 * * *` |
| `LIMPIEZA_RETENCION_SESIONES` | How long a dead session and its expired refresh tokens are kept. Never shorter than the access token lifetime. | `7d` |
| `LIMPIEZA_RETENCION_IDEMPOTENCIA` | How long a spent idempotency key is kept | `24h` |
| `SIMULACION_TICK` | How often the clock of the stores that asked for it advances one tick. A store in `MANUAL`, which is the default, never moves by itself. | `30s` |
| `CACHE_VERSIONES` | Keeps what a published version says in memory. `false` turns it off and the API answers the same, only slower. | `true` |
| `CACHE_VERSIONES_MAXIMO` · `CACHE_VERSIONES_INACTIVIDAD` | Versions each cache remembers at a time, and how long an unused entry is kept | `200` · `2h` |
| `DB_POOL_SIZE` | Connections to PostgreSQL. Requests run on virtual threads, so this is the real ceiling of concurrent work (`prod`) | `10` |
| `DB_STATEMENT_TIMEOUT` | The longest a statement of the API can run before PostgreSQL cancels it. Each connection gets it when it takes the role `procesos_app`; Flyway migrates without it. `0` is no limit | `30s` |
| `DB_IDLE_IN_TRANSACTION_TIMEOUT` | The longest a transaction of the API can stay open doing nothing: past it, PostgreSQL closes the connection and the pool replaces it | `60s` |
| `DB_LEAK_DETECTION_THRESHOLD` | Milliseconds a connection can stay out of the pool before the log warns of a leak, with the stack of the code that took it. `0` turns it off; less than `2000` is not accepted | `20000` |
| `MANAGEMENT_PORT` | Port of Actuator in `prod`: the probes and Prometheus read it inside the network, and it is not published | `8081` |
| `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | Where to send the spans, over OTLP and HTTP, for example `http://collector:4318/v1/traces`. Unset, no span leaves the API, and the `traceId` still reaches the logs and the errors. Leave it out rather than empty: an empty value counts as set | None |
| `TRACING_SAMPLING` | Share of the requests whose trace is sent, from `0` to `1` | `0.1` |
| `SHUTDOWN_TIMEOUT` | On shutdown, how long the server waits for the requests in progress to finish | `20s` |
| `GEMINI_API_KEY` | Key for the AI review. Without it the review answers `503` and nothing else changes. | None |
| `GEMINI_MODEL` · `GEMINI_BASE_URL` · `GEMINI_TIMEOUT` | Model, address and how long to wait for it | `gemini-3.8-flash` · Google endpoint · `20s` |
| `REVISION_MAX_REVIEWS` · `REVISION_WINDOW` | Reviews a store can ask for, and the window that counts them | `10` · `1h` |
| `SMTP_HOST` · `SMTP_PORT` | The SMTP server of the e-mail provider. Left empty, the API starts and every e-mail waits in the queue of jobs, failing, until there is one. `dev` uses Mailpit | Empty · `587` |
| `SMTP_USERNAME` · `SMTP_PASSWORD` | Credentials of that server | Empty |
| `SMTP_AUTH` · `SMTP_STARTTLS` | Whether the server asks for them, and whether the connection switches to TLS | `true` · `true` |
| `MAIL_FROM` | The sender of every e-mail. Its domain needs SPF, DKIM and DMARC ([runbook](runbook.md)) | `BPMN Process Manager <no-reply@localhost>` |
| `APP_PUBLIC_URL` | The address of the web app, where the links of the e-mails go | `http://localhost:4200`; `http://localhost:WEB_PORT` in the Compose stack |
| `TRABAJOS_TRABAJADORES` | Jobs of the queue this instance runs at once. Each one holds a connection of the pool while it runs | `2` |
| `TRABAJOS_MAXIMO_EN_CURSO_POR_TIENDA` | Jobs of one store running at once, counted across every instance, so a big export does not hold up the other stores | `2` |

In `prod` the logs are JSON in the Elastic Common Schema, one line per event with the `requestId` of the request that
wrote it; in `dev` they stay in plain text, which is easier to read in a terminal.

On startup, Flyway creates the schema or brings it up to date. The database must exist, and its user needs permission
to create tables.

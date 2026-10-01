[← Documentation](README.md)

# Configuration

## Profiles

| Profile | Activated by | Database | Demo Store | H2 console | OpenAPI and Swagger UI | SQL log |
|---|---|---|:---:|:---:|:---:|:---:|
| `dev` | Default, when no profile is set | H2 file under `./data` | ✓ | ✓ | ✓ | ✓ |
| `test` | `@ActiveProfiles("test")` in integration tests | In-memory H2, a new one for each Spring test context | — | — | ✓ | — |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | PostgreSQL | — | — | — | — |

The tests tagged `postgres` leave the profile's database aside and run against a PostgreSQL 16 container;
[Quality and testing](testing.md) says what they are for and how to run them.

## Environment variables

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
| `LIMPIEZA_CRON` | When the nightly purge runs. `-` turns it off. | `0 30 3 * * *` |
| `LIMPIEZA_RETENCION_SESIONES` | How long a dead session and its expired refresh tokens are kept. Never shorter than the access token lifetime. | `7d` |
| `LIMPIEZA_RETENCION_IDEMPOTENCIA` | How long a spent idempotency key is kept | `24h` |
| `SIMULACION_TICK` | How often the clock of the stores that asked for it advances one tick. A store in `MANUAL`, which is the default, never moves by itself. | `30s` |
| `CACHE_VERSIONES` | Keeps what a published version says in memory. `false` turns it off and the API answers the same, only slower. | `true` |
| `CACHE_VERSIONES_MAXIMO` · `CACHE_VERSIONES_INACTIVIDAD` | Versions each cache remembers at a time, and how long an unused entry is kept | `200` · `2h` |
| `DB_POOL_SIZE` | Connections to PostgreSQL, the real ceiling of concurrent work (`prod`) | `10` |
| `MANAGEMENT_PORT` | Port of Actuator in `prod`: the probes and Prometheus read it inside the network, and it is not published | `8081` |
| `SERVER_THREADS` | Threads that serve requests; the rest queue up (`prod`) | `200` |
| `GEMINI_API_KEY` | Key for the AI review. Without it the review answers `503` and nothing else changes. | None |
| `GEMINI_MODEL` · `GEMINI_BASE_URL` · `GEMINI_TIMEOUT` | Model, address and how long to wait for it | `gemini-3.8-flash` · Google endpoint · `20s` |
| `REVISION_MAX_REVIEWS` · `REVISION_WINDOW` | Reviews a store can ask for, and the window that counts them | `10` · `1h` |

In `prod` the logs are JSON in the Elastic Common Schema, one line per event with the `requestId` of the request that
wrote it; in `dev` they stay in plain text, which is easier to read in a terminal.

On startup, Flyway creates the schema or brings it up to date. The database must exist, and its user needs permission
to create tables.

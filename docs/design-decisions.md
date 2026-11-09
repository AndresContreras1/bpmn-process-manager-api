[← Documentation](README.md)

# Design decisions

The short reasons behind the choices that are not obvious. The architecture decisions, each with its context and
what checks it, have a [record](adr/README.md) of their own.

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
- **A version is a snapshot, not a copy of the process.** Publishing stores the diagram as JSON instead of cloning
  the process with its whole model. Cloning would break the unique name per store, multiply rows, and force every
  modeling service to know about versions; a snapshot never changes, so it is also the obvious candidate for a
  second-level cache. What tells two versions apart is the SHA-256 fingerprint of the diagram reduced to what draws
  it, so saving without changing anything does not count as a change.
- **A case is a list of steps, not a table of tokens.** Every time a case goes through a node it leaves a row with
  the name the node had and its state, so reading a case is reading where it has been. The live tokens are the rows
  that are still pending or waiting, and the tray of tasks is a query over them. A table of marks would be exact and
  unreadable.
- **A row lock guards a case, not a version number.** Completing a task, receiving a message and moving the clock
  can all touch the same case, and they touch different rows of it, so optimistic locking would let two of them
  through. Every operation that moves a case starts by locking its row, and whoever completes a task locks the case
  **before** reading the task: the other way round, the second person would keep the state they read before waiting
  and complete it twice.
- **Conditions have their own language.** What is written on a flow is written by a user, so it is read with a
  grammar of the project's own — variables, six comparators, `and`, `or`, `not` and brackets — and never with SpEL
  or a script engine, which would let that text call methods. The same parser answers the diagnosis when publishing
  and the engine when running, so a condition that publishes is a condition that runs, and an ArchUnit rule keeps
  anything that executes code out of the build.
- **A missing variable is false, and says so.** A comparison over a variable the case does not have is false rather
  than an error, and leaves a note in the timeline. An order does not fall over because a partner sent one field
  less; it stops at the gateway that has nowhere to go, with the reason written down.
- **The queries of the execution are named queries.** The reads the operation depends on live on the entity as
  `@NamedQuery`, so a renamed one fails to start the application and its test, instead of failing a request on a
  Tuesday afternoon.
- **Single-table inheritance for flow nodes.** Activities and gateways share one table and one identity, so sequence
  flows can point to either of them.
- **Soft delete everywhere.** Processes and process roles carry their own `activo` flag. BPMN elements use Hibernate's
  `@SQLDelete` and `@SQLRestriction`, so a delete becomes an update and no query sees retired rows. Hibernate's
  `@SoftDelete` would have forced eager to-one associations, against the project's lazy-loading rule. A unique
  constraint that a retired row would still hold, such as the pair of nodes of a sequence flow, only counts active
  rows.
- **One job deletes, and only technical rows.** Sessions, refresh tokens and idempotency keys are the only
  tables that grow with nobody reading them back, so a nightly sweep is the single physical `DELETE` in the API,
  and the only place that crosses stores on purpose: it reads nobody's data, it drops rows that are useless to
  everyone. A session waits until it has no refresh token left and until no access token of it can still be alive.
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
  `gestion` knowing `modelado`, so the modules can grow without a cycle. The same holds between the four top-level
  packages: what everyone needs sits in `common` rather than being borrowed from a neighbour, and an ArchUnit rule
  fails the build the day someone points one of them backwards.
- **The schema belongs to Flyway.** Migrations are the single source of truth, and Hibernate only validates them
  (`ddl-auto=validate`). Portable SQL lives in `db/migration/common`. What only one engine can express, such as
  PostgreSQL's partial unique indexes, lives in `db/migration/{vendor}`, and H2 gets an equivalent built on a
  generated column. Each branch is proved against its own engine: the H2 one by the build of every day,
  the PostgreSQL one by the suite that runs on a container.
- **A published diagram is text in the row, not a large object.** Hibernate turns `@Lob` on a `String` into
  an `oid` in PostgreSQL: the document moves out of the table into the large-object store, with its own
  identity and its own cleanup, and plain SQL stops reading it. The column asks for `text` and the field
  asks for the JDBC type that matches it, so the JSON stays in the row on both engines.
- **Every text column has a length, and so does its request field.** A value that is too long answers `400` before it
  reaches the database. Passwords stop at 72 characters, because BCrypt only reads 72 bytes and Spring Security
  rejects longer ones.
- **Tests never touch the development database.** Every `@SpringBootTest` and every `@DataJpaTest` declares its
  profile, which an ArchUnit rule checks, and the `test` profile gives each Spring context its own in-memory
  database. The persistence slices keep that database rather than the one the slice would substitute, so they run
  against the schema Flyway creates, check constraints included.

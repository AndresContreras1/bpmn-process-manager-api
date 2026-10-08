[← Documentation](../README.md)

# Decision records

Each record here is one decision: the context it was taken in, what it changed, and what in the repository checks
it, whether a test, an ArchUnit rule or a CI job. The pages of the documentation hold the detail.

Records 0001 to 0023 are decisions D1 to D23, taken together in plan v3. All of them are in the code except 0023,
which was never built. Decisions from D24 onward get their record with the change that applies them, each one under
the next number.

Some ArchUnit messages cite `ADR-001` (packaging by module) and `ADR-002` (the store on every row). Those are earlier
records that are not kept in this repository, not 0001 and 0002.

| # | Decision | Status |
|---|---|---|
| 0001 | [Four modules, one direction of dependency](0001-four-modules-one-direction.md) | Accepted |
| 0002 | [Publishing creates an immutable version; the live model is the draft](0002-publish-into-immutable-versions.md) | Accepted |
| 0003 | [A case advances with its row locked](0003-lock-the-case-row.md) | Accepted |
| 0004 | [The state of a case is its steps, not a table of tokens](0004-steps-instead-of-tokens.md) | Accepted |
| 0005 | [A bounded BPMN semantics, written down](0005-bounded-bpmn-semantics.md) | Accepted |
| 0006 | [A condition language of its own, which runs no code](0006-own-condition-language.md) | Accepted |
| 0007 | [Partners are adapters behind a port, and today all of them are simulated](0007-partners-behind-a-port.md) | Accepted |
| 0008 | [The clock is moved by whoever tests](0008-clock-moved-by-the-caller.md) | Accepted |
| 0009 | [Trays instead of queues](0009-trays-instead-of-queues.md) | Accepted |
| 0010 | [Multi-tenancy holds in execution too](0010-tenancy-in-execution.md) | Accepted |
| 0011 | [Events are the third subtype of `NodoFlujo`](0011-events-as-flow-nodes.md) | Accepted |
| 0012 | [Messages are anchored to nodes](0012-messages-anchored-to-nodes.md) | Accepted |
| 0013 | [Trays by process role; memberships are optional](0013-trays-by-process-role.md) | Accepted |
| 0014 | [A deterministic diagnosis, in two levels](0014-deterministic-diagnosis.md) | Accepted |
| 0015 | [A history for the whole store](0015-store-history.md) | Accepted |
| 0016 | [A structure policy per store](0016-structure-policy-per-store.md) | Accepted |
| 0017 | [Temporary passwords, changed before anything else](0017-temporary-passwords.md) | Accepted |
| 0018 | [No Thymeleaf; a single-page app behind NGINX](0018-spa-behind-nginx.md) | Accepted |
| 0019 | [Cache only what never changes](0019-cache-only-the-immutable.md) | Accepted |
| 0020 | [A scheduled purge of technical rows](0020-scheduled-purge.md) | Accepted |
| 0021 | [Testcontainers as a tagged suite](0021-tagged-postgres-suite.md) | Superseded by 0041 |
| 0022 | [Named queries where startup checks them](0022-named-queries.md) | Accepted |
| 0023 | [Optional shared state](0023-optional-shared-state.md) | Not implemented; superseded by 0034 |
| 0029 | [The browser's session in HttpOnly cookies](0029-browser-session-in-cookies.md) | Accepted |
| 0034 | [PostgreSQL for all shared state](0034-postgresql-for-shared-state.md) | Accepted |
| 0041 | [PostgreSQL in development and in the tests](0041-postgresql-everywhere.md) | Accepted |

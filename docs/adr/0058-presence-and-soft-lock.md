[← Decision records](README.md)

# ADR-0058: Presence over SSE and a soft lock

- **Status:** Accepted
- **Decided in:** plan v5 (PR 58, D28)

## Context

Two people editing the same process should see each other before stepping on each other. A CRDT does not guarantee
valid BPMN, and the product runs on more than one replica.

## Decision

- **Server-sent events**, `GET /procesos/{id}/en-vivo`: `presencia`, `bloqueo`, `guardado`, `publicado` and
  `control-tomado`. Authorized when subscribing, at most five connections per person, a heartbeat comment every 25
  seconds, and no buffering in the proxy.
- **Presence lives in PostgreSQL**, and events travel with `NOTIFY`: they leave when the transaction commits, so a
  rollback tells nobody, and every replica forwards them to its own connections. A notification carries little: the
  list of people is read from the database on arrival, because `NOTIFY` is limited to 8000 bytes.
- **The heartbeat is per replica**, so it is a thread of its own and not an `@Scheduled`, which runs on one replica. Rows
  nobody marks for 60 seconds are swept, like those of a replica that died.
- **The lock is soft:** the first who edits has it and loses it after 60 seconds without a heartbeat. Others watch and
  may **take control**, which is audited and tells the previous editor. It does not stop saving: `If-Match` does that.

## Consequences

- No CRDT in 1.0 (D28): one person edits at a time.
- A connection outlives its access token: it was authorized when it opened.

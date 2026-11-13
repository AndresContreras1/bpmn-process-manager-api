[← Decision records](README.md)

# ADR-0067: Comments, mentions and notifications

- **Status:** Accepted
- **Decided in:** plan v5 (PR 63)

## Context

The people who run a process talk about it: a question about one step, a doubt about a published version, an answer
from whoever knows. That conversation belongs next to the model, and those it involves have to hear about it without
watching the page, in the app and by e-mail, as often as they choose.

## Decision

- **Threads in `modelado`.** A thread is a first comment on a process, optionally anchored to one element by its
  `bpmnId` and to one published version; its replies point to it and inherit the anchor. Replying to a reply answers
  in its thread, so threads stay flat. Only the first comment carries the state: open, or resolved with who and when.
  An element is checked against the draft; with a version, it is taken as the web read it from that version's
  drawing, since it may be gone from the draft.
- **Markdown like the documentation.** The same converter and whitelist as ADR-0066; both the Markdown and the
  cleaned HTML are kept, up to 10 000 characters.
- **Writing is `COMENTAR`, and it needs `VER` too.** Every role comments, the read-only one included, so the route
  matrix lets every role write to the comment routes and the resource rule maps them to `COMENTAR` on the process of
  the comment. The service also asks `VER`: a grant that reserves viewing hides the process from whoever lacks it,
  and they cannot write in it either. Editing is the author's; deleting, the author's or an administrator's.
- **Mentions are ids, checked and refused.** The web resolves names; the request carries ids, at most 20. Each has to
  be an active person of the store who can view the process, through `Permisos`. Any other id answers `400` naming
  its position, with the same reason whether the person cannot view the process, is inactive, belongs to another
  store or does not exist: the error never tells whether someone exists. Refusing beats dropping, because the author
  would believe someone was told.
- **Deleting empties.** A deleted comment loses its text and mentions and keeps its row: a first comment with replies
  keeps its thread, shown as deleted, and a thread with nothing left disappears from the list.
- **Notifications in `gestion`, asked for by event.** `modelado` publishes `NotificacionPedida` (ids only, in
  `common`), which waits in Spring Modulith's outbox in the comment's transaction, like the audit trail's events;
  `gestion` turns it into one notification per person after the commit. A rolled-back comment tells nobody, and a
  failure to notify never rolls back the comment. Types now: `MENCION`, `RESPUESTA` (to everyone else who wrote in
  the thread and is not already mentioned) and `HILO_RESUELTO`. A type is an enum constant with its default e-mail,
  a value in the table's check and three message keys per language.
- **Three ways out.** Every notification goes to the table, the person's center, and at once to their open
  server-sent event streams, through `NOTIFY` like presence (ADR-0058): one listening connection per instance now
  serves every channel. E-mail follows the person's preference per type: `INMEDIATO` through the job queue,
  `RESUMEN_DIARIO` in one daily job (ShedLock, a fixed UTC hour), `NUNCA` none. An e-mail only counts what is still
  unread, is written in the person's language with a link to the web, and never carries the comment's text: it says
  who, what and where.
- **A notification is its recipient's alone.** Every query filters by store and person; another's answers `404`.
  It stores ids, not names or text, so anonymizing someone changes how they appear in it.
- **Anonymizing keeps the conversation.** Names are read when shown, so the author and the mentions show the
  pseudonym; the text that mentioned the person as `@Name` is rewritten with it, in the same transaction, by an event
  that is not stored anywhere. The person's own notifications and preferences are deleted.
- **Read notifications are purged** after 90 days (`NOTIFICACIONES_RETENCION`) by the nightly purge; unread ones stay.

## Consequences

- The daily digest goes out at the same UTC hour for everyone; the person's time zone does not move it.
- The limit of five open streams per person is counted on each instance, not across the cluster.
- The plan's history of the process records changes to the model; comments are a conversation, so they go to the
  audit trail (resolve, reopen and delete) and not to that history.
- PR 64 adds review requests and PR 71 deadlines as new types on the same tables.

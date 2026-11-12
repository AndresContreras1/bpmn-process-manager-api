[← Decision records](README.md)

# ADR-0069: Review and approval before publishing, and recertification

- **Status:** Accepted
- **Decided in:** plan v5 (PR 64)

## Context

Some stores want a second pair of eyes before a process changes for everyone: someone models, others approve, and
only then does the new version come into force. Others are a handful of people who publish directly, as they always
have. A published process also ages: someone has to confirm from time to time that it still describes what the store
does. D2 (immutable versions with a pointer to the one in force) and the publishing `PATCH` already work and are used
by the web; approval cannot break them (D32).

## Decision

- **A policy per store, off by default.** `politicas_publicacion` holds at most one row per store: `DIRECTA` (what a
  store without a row has) or `CON_APROBACION`, with N approvals (1 to 10) from a list of people (up to 50) or from a
  process role, never both. Saving it with approval checks that the chosen source has at least N active people. Only
  the store administrator changes it (`PUT /empresas/actual/politica-de-publicacion`, with its version); every role
  reads it, so the web knows whether to offer publishing or asking for a review. Store-level settings live under
  `/empresas/actual`, like the configuration and the domains, not under a new `/organizacion` root.
- **Approval is a layer over D2, not a second way to publish.** The publishing `PATCH` keeps its request and its
  response. With the policy on, it answers `409` for everyone, the administrator included, with a problem that says a
  review has to be asked for. There is no bypass: an administrator who must publish at once turns the policy off,
  which is visible and audited, and turns it back on.
- **Asking for review is `PUBLICAR`.** It is the first step of publishing, and D31 grants are not to be bypassed: a
  person to whom a grant denies publishing a folder cannot publish it either by asking others to approve. `MODELAR`
  would let anyone who edits put a version into force with two friendly approvals. Deciding is `APROBAR` on the process
  plus being one of the request's approvers; reading is `VER`.
- **The request freezes the draft twice.** It saves a draft revision (motivo `SOLICITUD`, PR 40), the point to come
  back to, and the snapshot that will be published (definition, hash and BPMN, as a version keeps them) with the
  number it will have and the version in force it was compared with. Freezing has the conditions of publishing: no
  diagnosis errors and something different from the version in force. Editing the draft afterwards does not change
  what is reviewed or what is published. Old revisions may be pruned; the request keeps its own snapshot.
- **The approvers are fixed when it is asked for.** They are the people of the list or of the role who are active and
  can view and approve that process according to the single evaluator (D31), without the requester: whoever asks never
  approves, and the separation of duties of PR 60 keeps out whoever changed the process last. If fewer than N remain,
  the request is refused with `409`. Requests already open keep the approvers and the N they were opened with when the
  policy changes.
- **One open request per process,** enforced by a partial unique index as well as by the service, and the process row
  is locked while one is opened.
- **Decisions.** Each approver approves, asks for changes or rejects, once. Asking for changes and rejecting need a
  reason in Markdown (cleaned like comments, ADR-0067) and close the request. The approval that completes N publishes
  the frozen snapshot through the same code as the `PATCH` (`registrarPublicacion`: version, state, history,
  `ProcesoPublicado` event and audit entry with the request). The request row is locked while a decision is taken, so
  two last approvals cannot both miss the count. The requester withdraws an open request; whoever can administer the
  process can withdraw one that has nobody following it.
- **A stale request does not publish.** Before publishing, the frozen snapshot is checked again: if the version in
  force changed since it was asked for (another version was published or withdrawn), what was approved is no longer
  the difference people saw; if the diagnosis of the frozen diagram is no longer clean (a called process was retired,
  or the calls became circular), it cannot be published. Either way the request ends `APROBADA_SIN_PUBLICAR` with the
  reason, and the approval is kept.
- **The difference is PR 57's.** The request answers with the semantic comparison between the version in force when
  it was asked for and the frozen snapshot, computed when read; without a version in force, against an empty diagram.
  Lists leave it out.
- **Notifications through PR 63.** `APROBACION_PEDIDA` to the approvers; `SOLICITUD_APROBADA` (digest by default),
  `SOLICITUD_PUBLICADA`, `SOLICITUD_SIN_PUBLICAR`, `CAMBIOS_PEDIDOS` and `SOLICITUD_RECHAZADA` to the requester. Each
  type says what its reference points at, so the e-mail links to `?solicitud=` or `?comentario=`, or only to the
  process.
- **Recertification.** A process gets `meses_entre_revisiones` (1 to 60, 12 by default, set in its file).
  "Still valid" (`POST /procesos/{id}/recertificacion`) is for its owner, whatever their role, or whoever can
  administer the process: it moves the next review to today plus those months and records who, when, the note and
  whether it was overdue in the audit trail and the history. A daily scheduled job (ShedLock, a fixed UTC hour,
  `RECERTIFICACION_CRON`) puts one job per store in the queue; the job tells the owner when the date arrives and once
  more when it has passed. The date each warning was sent for is stored, so a repeated job warns nobody twice and a
  new date gets its own two warnings. Process responses carry `revisionVencida`, computed against the API clock.
- **Anonymizing** a person removes them from the policy's list and withdraws their open requests with a reason; their
  decisions stay, shown with the pseudonym. Deleting a store deletes its policy, requests and decisions.

## Consequences

- With approval on, publishing takes at least one other person; a store of one cannot turn it on usefully.
- An approver who edits the draft while a request is open becomes the last to change the process and, by the
  separation of duties, can no longer approve it.
- The drawing of a version (ADR-0066) is still sent by the web after publishing: after the last approval, by the web
  of whoever approved.
- The web screens (asking, the inbox, the request with its difference) come with F21; the portal shows overdue
  reviews with F22.

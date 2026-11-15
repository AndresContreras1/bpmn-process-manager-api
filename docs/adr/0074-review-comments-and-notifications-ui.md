[← Decision records](README.md)

# ADR-0074: Review, comments and notifications in the web

- **Status:** Accepted
- **Decided in:** plan v5 (F21)

## Context

The API keeps comment threads on processes, elements and published versions, notifies each person of what concerns
them in a table and a server-sent events stream (ADR-0067), and lets a store publish only through a review that
others approve (ADR-0069). The web has to make that usable from the diagram itself, keep a live count without
polling, never show anything but the HTML the API cleaned, and stay within the initial bundle budget.

## Decision

- **Asking for a review replaces Publish.** The process page and the modeler read the store's policy and the
  process's open request. With `CON_APROBACION` the Publish button becomes *Ask for review*, which saves what is left
  in the modeler first, because the API freezes the saved draft; like Publish, it waits until the draft is saved,
  and if that save fails it says why instead of sending what is on screen. With an open request it becomes a link to
  it. If the policy cannot be read, Publish stays and the API's `409` says why it refused.
- **A request is shown as a comparison.** Its page draws the frozen draft revision
  (`GET .../revisiones/{id}/modelo?para=dibujar`, so an approver who may view the process but not export it still
  sees it) with the same markers as the compare page, and lists the difference with the same legend component as the
  compare page and the modeler: a count per kind, then added, changed, only moved and removed, each group by name, so
  color never carries it alone and what was removed, which is not in the drawing, is still there. When the revision was already cleaned, a published request shows its version and an
  unpublished one today's draft, with a notice. Approve, ask for changes and reject are shown only to an approver who
  has not decided; the last two require a text. Withdraw is offered to the requester and to whoever administers the
  process, which the page asks from `GET /procesos/{id}/permisos`, keeps that right while the request stays open, and
  asks for confirmation, since a withdrawn request cannot be reopened.
- **Comments live in a panel beside the canvas.** The panel shows the threads of the selected element, or of the
  whole process when nothing is selected, in what is on screen: the draft or the published version chosen. It reads
  every thread of the process once, 50 per page, counts the open ones per element, and the canvas draws that number
  as a button over each element that selects it. After any change it reads them again, since someone else may have
  written. A link with `?comentario=` (the bell and the e-mails) opens the thread's version, selects its element and
  focuses the thread.
- **Mentions are chosen, not typed.** A combobox asks `GET /procesos/{id}/mencionables`, a route this PR adds: the
  active people of the store who can view the process, without whoever asks, by name and up to 20, judged with one
  evaluation of grants for the first 50 candidates. The chosen people travel as ids; the Markdown is not rewritten.
  The list leaves out who is already chosen and stops at 20, which the API would refuse. Typing and the arrow keys
  go through one search, so a newer one cancels the older, and an answer for other text, or one that arrives after
  the field lost focus, is dropped. The name filter takes `%` and `_` literally.
- **The bell is fed by the stream alone.** It opens `/notificaciones/flujo` while someone is signed in and takes the
  unread count from it, which the API sends on opening and on every change, so a reconnection catches up by itself.
  The reconnection is the one of the live modeler (ADR-0061), made generic over the event types: double the wait
  each time up to 30 s, randomly from half, also after a `429`. A new notification is announced in an `aria-live`
  region even with the panel closed. The bell arrives in its own chunk when the browser is idle, and every new screen
  is a lazy route: the initial bundle went from 732 kB to 738 kB.
- **Preferences send only what changed.** The settings page shows each type with its three choices and saves the
  types whose choice differs, so a type nobody touched keeps following its default.

## Consequences

- The counts on the canvas and the panel come from one read of every thread, so a process with thousands of threads
  costs as many pages; a count per element in the API would save them, and would be a new route.
- Two browsers of the same person open two streams, and the API keeps five per person on each instance: a sixth tab
  waits, retrying, until one closes.
- With the stream open, marking one as read leaves the count to the stream, which sends the new count sometimes
  before the HTTP answer; only with the stream closed does the web lower it by itself.
- A request whose frozen revision was cleaned and that was never published is drawn on today's draft; the list still
  describes exactly what it changes.

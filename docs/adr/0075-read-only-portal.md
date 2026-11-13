[← Decision records](README.md)

# ADR-0075: A read-only portal of what is published

- **Status:** Accepted
- **Decided in:** plan v5 (F22)

## Context

Most people in a store read processes and never change them, yet the web showed them the whole application: the
process list with its drafts, the modeler, the comparison, the filing card. The API already serves everything a
reader needs, each read guarded by `VER`: the folders, the search (ADR-0070), the published version's BPMN file for
drawing (`?para=dibujar`), the documentation as cleaned HTML and the characterization sheet (ADR-0066), the
documents with five-minute signed links (ADR-0065), the manuals, and whether a review is overdue (ADR-0069). What a
reader could not learn was who owns a process: the list of people is the administrator's, so the owner's id named
nobody.

## Decision

- **A portal of its own, two lazy routes.** `/portal` lists the published processes of a folder (`estado=PUBLICADO`,
  that folder and its subfolders, by name) beside the folder tree, without its editing controls, or what the search
  finds; the folder, the text and the page live in the address. `/portal/procesos/:id` is one process to read: the
  sheet, the diagram of the version in force and the manual. Both arrive only when opened; the initial bundle went
  from 738 kB to 739 kB, for the link in the sidebar.
- **The search is the one of Ctrl+K**, through the same service, without comments, which are working conversation:
  processes, elements and documents. An element, or a document attached to one, opens the process with that element
  chosen (`?elemento=`).
- **The diagram is navigable.** The published version is drawn with the bpmn-js viewer; the elements with
  documentation or documents are marked with a thicker line, and the help above the drawing says so. One click on an
  element shows, beside the drawing, its documentation (only the HTML the API cleaned, through Angular's sanitizer
  too) and its documents, each downloaded through a signed link asked for at the moment of the click. The structure
  view of the modeler, read-only, walks the same elements with the arrows, and Enter chooses one, so a keyboard reaches
  everything a mouse does; the choice is announced in an `aria-live` region.
- **No edit controls, driven by the permissions API.** The portal has no control that changes anything. The only
  request it can send is to build the manual, offered when `GET /procesos/{id}/permisos` says `EXPORTAR` and the access
  role may send a `POST` (the route matrix keeps those for administrators and editors: the read-only role exports by
  default but would get `403`). Without the permissions answer, nothing is offered. Everyone else downloads the
  newest manual that is ready for the version in force, in the reader's language when there is one; a manual of an
  older version describes a process that is no longer in force and is not offered.
- **The owner travels with the process.** `GET /procesos/{id}` adds `duenoNombre`, the owner's name, read in the
  same store; it is empty without an owner, or if the owner is no longer found. The portal also shows the
  classification, the next review and a *Review overdue* badge, in the list and on the page, from the
  `revisionVencida` the API works out with its clock.
- **Every role reaches it.** The read-only role keeps the rest of the web as before; the portal is a new entry in the
  sidebar, not a wall. Hiding a control is a courtesy: the route matrix and `ControlDeRecursos` still decide.

## Consequences

- The folder tree counts every process of a folder, drafts included, while the portal lists only what is published;
  a count of published processes per folder would be a change to `GET /carpetas`.
- The search answers a page of 20 and the portal drops the comments from it, so a page can show fewer. The API
  answers each element once, from the draft when it is also in the version in force, so the portal cannot tell an
  element found only in the draft: its page opens without choosing it. A filter of the API by what is published
  would solve both.
- Documentation belongs to the process, not to a version, so the portal shows what is written today next to the
  version in force; an element removed from the draft keeps its text until someone deletes it.
- The read-only role still sees the whole web: sending it straight to the portal, or hiding the rest, is a decision
  about the product, left for later.

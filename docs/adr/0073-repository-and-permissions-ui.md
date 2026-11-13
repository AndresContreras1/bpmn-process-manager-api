[← Decision records](README.md)

# ADR-0073: The repository, the search and the permissions in the web

- **Status:** Accepted
- **Decided in:** plan v5 (F19)

## Context

The API files processes in a tree of folders (ADR-0062), grants actions on folders and processes (ADR-0063) and
searches what each person can view (ADR-0070). The web has to let people use that without a mouse, without hiding
from them why something is refused, and without growing the bundle every visit downloads.

## Decision

- **The filters of the process list live in the address.** Folder, owner, tag, classification, review date, state,
  category, name, order and page are query parameters, read on every change of the address and written back with
  `replaceUrl`, so a filtered list can be shared, survives a reload and Back leaves the screen instead of undoing a
  letter. The address is typed by anyone, so a value that does not exist falls back to the default instead of
  reaching the API as a `400`.
- **Moving is filing elsewhere.** Moving a process sends its whole filing card with only the folder changed, from a
  dialog where the tree is a group of radio buttons: the arrows choose, Enter moves, Escape closes and the focus goes
  back to the button that opened it. Nothing needs dragging. A folder moves by choosing its new parent among the
  folders that are not inside it.
- **One search, in the header and on Ctrl+K or Cmd+K.** The header search by name became a button that opens the
  global search, which finds processes by name too. It is a WAI-ARIA combobox: the focus stays in the field, the
  arrows move the active option across the groups (processes, elements, documents, comments), Enter opens it and
  Escape closes. It asks 250 ms after the last key and drops the answer to a text that changed. The fragment is shown
  segment by segment as interpolated text in a `mark`, never as HTML. The dialog arrives the first time it is opened,
  and the shell no longer needs the forms package: the initial bundle went from 789 kB to 732 kB.
- **Buttons follow `GET /procesos/{id}/permisos` and the route matrix.** Edit and Model need `MODELAR`, Publish
  `PUBLICAR`, the BPMN file `EXPORTAR`, Permissions `ADMINISTRAR`, and Share and Delete `ADMINISTRAR` and an
  administrator, because the route matrix keeps them for one. Without that answer the buttons follow the role, as
  before the grants. The API stays the authority: a refusal still answers `403`, and the screens of grants show its
  own message, separation of duties included.
- **Inherited grants are read from each folder above.** The grants of a process are the ones written on it; the
  screen also lists, as inherited and read-only, the ones of every folder that contains it. A folder the person does
  not administer answers `403`, and the screen says so instead of hiding that something is inherited.

## Consequences

- A list that the API filters differently because the grants changed still shows the address that was shared; it is
  the API that decides what is in it.
- The list keeps showing row buttons by role: asking for the permissions of every row would be one request per row.
  The detail page is where the buttons are exact.
- Granting to a person needs the list of users, which only an administrator can read; an editor who administers a
  process grants to a role or to everyone.

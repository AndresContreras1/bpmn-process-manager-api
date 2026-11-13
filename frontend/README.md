# BPMN Process Manager web

The Angular front end of the [BPMN Process Manager API](../README.md). Each organization signs in, files its
processes in folders, models them on bpmn-js, publishes them and runs them, in Spanish, English or French.

## Stack

- Angular 22 with standalone components, `inject()` and the built-in control flow (`@if`, `@for`). Every component
  keeps the `Eager` change detection it had before Angular 22 made `OnPush` the default, until the move to signals.
- Bootstrap 5.3, customized through its Sass variables, and Font Awesome icons. Of Bootstrap's JavaScript only the
  modals, the dropdowns and the collapse are loaded, from their ES module sources, so the initial load stays under its
  800 kB budget.
- RxJS and `HttpClient` to talk to the REST API.
- bpmn-js 18 for every drawing, with modules of its own in `src/app/bpmn/modulos`: the palette, the rules and the
  `bpm:` extension of the model, and a Spanish translation. It is downloaded when a screen with a diagram opens, its
  styles with it, so the screens without one do not pay for it.

## Run it

Requirements: Node.js 24 (or 22.22 and later), as Angular 22 asks, and the backend running on port
8080 (see [Getting started](../docs/getting-started.md)).

```bash
npm ci
npm start
```

The app opens on http://localhost:4200. In development it calls the API with relative URLs, and the Angular dev server
forwards every `/api` request to http://localhost:8080 (`proxy.conf.json`). The browser only talks to one origin, so
the app works on any port without touching the backend's CORS configuration. If port 4200 is taken, pick another one:

```bash
npm start -- --port 4201
```

On Windows PowerShell, if running scripts is disabled, call `npm.cmd` instead of `npm`.

A production build goes to `dist/bpmn-process-manager-web/browser/`, one folder per language:

```bash
npm run build
```

`npm start` serves the source language, English. To work on another one:

```bash
npm start -- --localize=es-CO
```

What the pipeline checks before it builds:

```bash
npm run lint           # angular-eslint over the TypeScript and the templates, accessibility rules included
npm run format:check   # Prettier over src; npm run format writes it
npm test               # Vitest over the logic that is not a screen: the rules of the modeler, the structure
                       # tree, the autosave, the live connection, the helpers and the session
```

## Languages

[ADR-0050](../docs/adr/0050-one-build-per-language.md): `@angular/localize`, with one build per language, each in its
own folder: `es-CO`, `en-US`, `en-AU` and `fr-FR`, with the dates, numbers and currency of each (COP, USD, AUD and
EUR). English (en-US) is the language of the source.

- **Marking texts.** A template marks what a person reads with `i18n` and `i18n-<attribute>`, and the code with
  `$localize`. A text that depends on a value is a block (`@if (enviando) {Saving…} @else {Save}`) or a whole plural
  (`{n, plural, =1 {1 case} other {{{ n }} cases}}`), never a string inside an expression, so that each language can
  write the sentence its own way.
- **Translations.** `npm run extract-i18n` writes the messages to `src/locale/messages.json`; each language has its
  `messages.<language>.json` beside it. The build stops on a message without its translation.
- **Which one opens.** NGINX serves each folder and sends any other path to the reader's language: the cookie
  `idioma`, which the language menu of the header sets, or else the browser's `Accept-Language`. After signing in, the
  web opens in the language that holds for the person: their own, chosen in *My account*, or their organization's,
  which an administrator sets in *Organization settings*. The same two places choose the time zone.
- **Dates.** The API answers in UTC; the `fecha` pipe shows a date in the time zone that holds for the person, in the
  language of the build. A date without a time, such as the day an organization registered, is that day everywhere.

## Design system

- **Tokens.** `src/design/tokens.json`, in the format of the W3C Design Tokens group (DTCG 2025.10), holds the colors,
  spacing, radii, font and layout sizes: a scale of ten steps of the green `#0f6e56`, the grays, four status colors,
  the dark palette and aliases such as `color.marca.primario`. `npm run tokens` (run by itself before `start`,
  `build` and `test`) turns them into `src/styles/_tokens.scss`: the Sass variables Bootstrap reads before it
  compiles, and CSS variables (`--bpm-…`) for the app's own styles. The generated file is not committed, so it never
  drifts from the tokens.
- **Contrast.** `contraste.spec.ts` measures every pair of text and background the app uses, in light and in dark,
  against WCAG 2.2 AA: 4.5 for text and 3 for the border of a control.
- **Dark mode.** The header switches `data-bs-theme` on the page, which Bootstrap reads; the choice is remembered in
  this browser, and without one the app follows the system.
- **Shell.** A header with the brand, the global search, the theme and the account; for whoever signed
  in, a sidebar with the sections (Processes, Work, Dashboard and, for administrators, Administration) and the
  breadcrumbs each route declares in `data.migas`. A skip link jumps to the content. The links keep the ids the
  navbar had, which the end-to-end suite looks for.
- **Components** in `components/ui`: loading (`role="status"`), empty, a load error with a retry button, the status
  badge with its icon, so status is not told by color alone, and the passing notices, in an `aria-live` region.
- Only the Bootstrap components the screens use are compiled: no accordion, progress bar, tooltip, popover,
  carousel or offcanvas.
- **Accessibility.** The end-to-end suite runs axe-core over every screen, signed in and not, in both themes, and
  fails on any serious or critical violation of WCAG 2.1 AA.

## Session

- **The app never sees a token** (D29). The session lives in two `HttpOnly` cookies that the API sets and clears.
  Who signed in lives in memory, not in the browser's storage: when it starts, the app asks `GET /api/v1/auth/csrf`
  for the `XSRF-TOKEN` cookie, which `HttpClient` copies into the `X-XSRF-TOKEN` header of every change, and then
  `GET /api/v1/auth/sesion` for the profile and how long the session has left. The route guards wait for that answer.
  `localStorage` keeps only a mark that there was a session, so that a visit without one asks nothing. The demo
  organization signs in with `admin@demo.com` / `every-order-on-time`.
- **The session renews itself.** The access token lives fifteen minutes, so a `401` does not mean the session is
  over: the interceptor asks `POST /api/v1/auth/refresh`, which trades the refresh cookie for new ones, and retries
  the request. The user sees nothing. The sign-in page comes back only when the renewal is itself rejected, and
  then it says why (`?sesion=vencida`).
- A refresh token works **once**, and sending it twice closes the session on purpose, as reuse detection. So the
  renewal is shared: every request that expires at the same moment waits for the same call. Opening a process runs
  two requests in parallel, and both are served by one renewal. Between tabs too: a Web Lock lets one tab renew at a
  time, and a tab that gets the lock just after another renewed only retries, since the cookies are already new.
- **Tabs talk to each other** through a `BroadcastChannel`: signing in, renewing and signing out reach every tab of
  the web. Signing out in one takes the others to the sign-in page, which says it happened in another tab
  (`?sesion=cerrada`).
- **A warning before the session closes.** Each session answer says how long the session survives without being
  renewed (the organization's idle timeout) and how long it has left. Two minutes before closing for being idle, a
  notice counts down and offers to stay signed in, which renews it; at zero the app forgets the session in every tab.
  Five minutes before its end, which cannot be extended, it says at what time it ends.
- A session counts as open while the refresh token exists, not while the access token is valid. Tying the route
  guard to the access token would throw the user out fifteen minutes in, which is the same bug seen from the
  other side.
- Every POST made with a session carries an `Idempotency-Key`. The API replays a repeated key instead of creating a
  second resource, which matters for the retry after a renewal and for a double click. The header is set before the
  request is signed, so the retry carries the same key.
- Signing out sends the refresh token, so the API closes that session instead of leaving a token that still renews.
- A route guard keeps private pages behind an open session and remembers the page the user asked for.
- `AuthService` publishes the current user through a `BehaviorSubject`, so the shell and the pages react to sign in
  and sign out.

## Account security

- **Second factor.** *My account → Security* sets up an authenticator app from a QR code (the `qrcode` library draws
  it; the key can be typed by hand too) and confirms it with its first code; then come ten recovery codes, shown once.
  With a second factor, the password leaves a half-done login and the sign-in page asks for the code, a recovery code
  or a passkey. Coming back from Google or Microsoft (`/login?paso=segundo-factor`) lands on the same step.
- **Passkeys.** Registered and removed from the same screen with `navigator.credentials`; `helpers/webauthn.ts`
  turns the API's JSON options into what the browser takes and the credential into the shape Spring Security reads.
  A passkey signs in without a password and also completes the half-done login.
- **Recent verification.** What removes a factor or closes the organization answers `403 Verificación requerida`
  when the person has not confirmed who they are in the last ten minutes. An interceptor opens a dialog that asks for
  the password, a code or a passkey, and repeats the request; cancelling leaves the `403`.
- **Links of the e-mails.** `/verificar-correo`, `/restablecer-clave` and `/aceptar-invitacion` read the token after
  `#`, which no server receives, and remove it from the address at once. `/recuperar-clave` asks for the link and
  answers the same whether the e-mail has an account or not. `/vincular` links a provider to an existing account.
- **Sessions and providers.** The same screen lists where the person is signed in, closes one session or all the
  others, and unlinks Google or Microsoft.
- **Administration.** *Organization settings* chooses who needs a second factor and whether people sign in only with
  a provider, verifies e-mail domains with a TXT record, and closes the organization (30 days of read-only grace,
  cancellable). *Users* invites by e-mail and anonymizes a deactivated person. The *Audit log* filters by action and
  dates, checks the chain and exports CSV or JSON.

## Processes

- The list shows the pages of 10 processes the API returns, the most recently changed first. Clicking a column title
  sorts by it (the `orden` parameter), and clicking it again reverses the order. The search box waits until the user
  stops typing and cancels the previous request (`debounceTime` and `switchMap`); the state and category filters work
  the same way, and clicking a category filters by it.
- **Folders** sit beside the list: a tree where a folder filters the list by itself and everything inside it, and
  where folders are created, renamed, moved inside another one and deleted, only when empty. A folder is offered only
  the folders that are not inside it. Tags, the owner (*Me* for anyone, and each person for an administrator), the
  classification and the date of the next review filter the list too, and the next review and the classification
  have columns of their own that sort.
- **The filters live in the address** ([ADR-0073](../docs/adr/0073-repository-and-permissions-ui.md)):
  `/procesos?carpeta=3&clasificacion=CONFIDENCIAL&orden=proximaRevision,asc&pagina=2` is a list anyone can open again,
  and a reload keeps it. Every change of a filter writes the address with `replaceUrl`, so Back leaves the screen
  instead of undoing a letter, and every change of the address, a link or Back included, reloads the list. What the
  address says is not trusted: a state, classification or order that does not exist, an id that is not a number or a
  badly written date fall back to the default. The pure functions are `filtrosDesdeUrl` and `filtrosEnUrl`.
- **Moving a process** opens a dialog with the tree as radio buttons: it opens on the folder the process is in, the
  arrows choose another, Enter moves it and Escape closes, and the focus goes back to the button that opened it. It
  saves the filing card with only the folder changed. The list has a Move button on each row, and the process page
  has one too.
- **A new process can be filed as it is created:** the form takes its folder (the one the list was showing, to start
  with), its owner (for an administrator), the date of its next review, its classification and its tags, and saves
  them with the filing card right after creating it. The tags are checked before sending by the API's own rule, at
  most 10 of at most 40 characters each. If that second call fails the process exists anyway, and its page says the
  filing is still to be done.
- **The filing card** sits under the diagram of a process: its folder, owner, next review, classification and tags,
  saved on their own with `PUT /procesos/{id}/ficha`, so filing a process is not a change waiting to be published.
- The API records the change history in Spanish, so the detail page shows each known entry in English and falls back
  to the original text for any other.
- Creating and editing share one reactive form. With an `id` in the route it loads the process and fills the form with
  `patchValue`; a name already used by another active process of the store shows up under the field (`409`).
- **Every change travels with the version it was made on.** The API has optimistic locking, so the edit sends the
  `version` that the `GET` returned and publishing sends it with the state. Without it the answer is a plain `400`.
- A `409` has several meanings and the `title` of the Problem Details tells them apart. `Conflicto de versión` means
  someone saved first: the form keeps what was typed and offers to reload, because reloading replaces it, while the
  detail page and the list reload on their own, because there nothing typed is lost. Any other `409` is a rule of the
  API, such as a repeated name or a diagram that cannot be published yet.
- The detail page shows the process and its change history. Publishing and deleting ask first in a Bootstrap modal,
  and the page changes only with the API's answer. A published process cannot go back to draft.
- **The buttons of a process follow what the API says the person can do with it**, `GET /procesos/{id}/permisos`:
  Edit, Model, Move and the filing card need `MODELAR`, Publish `PUBLICAR`, the BPMN file `EXPORTAR`, Permissions
  `ADMINISTRAR`, and Share and Delete `ADMINISTRAR` and an administrator, as the route matrix says. If that answer does
  not arrive they follow the role, as before: administrators and editors create, edit and publish, only
  administrators delete, and read-only users browse. The rows of the list ask the same, in parallel for the page on
  screen (at most 50), and keep the role's buttons until each answer arrives. The API decides either way and answers
  `403` otherwise, and a refusal that comes from a grant shows the API's own reason.

## Search

The header button and Ctrl+K, or Cmd+K, open the search of the whole organization (`GET /busqueda`): processes,
elements, documents and comments, only what the person can view. The dialog arrives the first time it opens.

- It is a WAI-ARIA combobox: the focus stays in the field, the arrows move the active result across the groups,
  Enter opens its process (an element opens with that element chosen, `?elemento=`), Escape closes, and the focus
  goes back where it was. Enter does nothing while the results on screen belong to a text that has changed since.
  Ctrl+K does not open it over another modal dialog, and the header shows the shortcut as Cmd K on a Mac. The number of results and the errors
  are read out in an `aria-live` region.
- It asks 250 ms after the last key, never twice for the same text, and throws away the answer to a text that has
  changed since (`buscarMientrasSeEscribe`, tested with fake timers).
- The fragment comes as plain-text segments. Each one is interpolated, the highlighted ones in a `mark`, so whatever a
  document or a comment says, `<script>` included, is read as text.

## Permissions

`/procesos/:id/permisos` and `/carpetas/:id/permisos`, from the process page and from the folder tree, list what is
granted there, grant an action to a person, a process role or the whole organization, and take a grant back.

- The grants of every folder above are listed too, as inherited and read-only, each linking to its folder. A folder
  the person does not administer cannot be read, and the screen says so.
- The form says what is missing before sending, by the same rule as the API: a person with `PERSONA`, a role with
  `ROL`, and nothing more. The list of people is the administrator's, so an editor who administers a process grants
  to a role or to everyone. It is read page by page, so a store with more than 50 people can pick any of them.
- What the API refuses is shown with its own message, separation of duties included.

## Diagram viewer

Every drawing is one component, `lienzo-bpmn`, on bpmn-js, in one of four modes: `ver`, `editar`, `caso` and
`comparar` ([ADR-0059](../docs/adr/0059-bpmn-js-modeler.md)). A process has one drawing, so a version, a case and the
modeler cannot disagree about it.

- The detail page asks in parallel for the BPMN file of the process (`GET /procesos/{id}/modelo?para=dibujar`), which
  bpmn-js draws, and for its whole diagram (`GET /procesos/{id}/diagrama`), which carries what a drawing does not say.
  `para=dibujar` asks for the file to draw it, which only needs permission to view; downloading it needs permission
  to export. If only the diagram fails, the rest of the page still shows.
- Clicking a task, a gateway or an event opens its details: lane, role, description, the flows that come in and go
  out with their conditions, and what it sends and waits for, each with the number the drawing puts on its message
  flow. A message there says how it travels, what the process does if it cannot be delivered, the fields it carries,
  the variable its body becomes and how it is matched to a case. The list under the drawing gives each message its
  content and correlation key.
- The field types are translated, like the change history: the API names them in Spanish.

### Versions and who is looking

- Publishing freezes the diagram as a version, so the page asks for the versions of the process and lets you pick
  one. A frozen version is drawn from the file written when it was published,
  `GET /procesos/{id}/versiones/{numero}/modelo`, with its diagram from `.../versiones/{numero}/diagrama`.
- The page says which of three cases you are in: the working copy, the working copy with changes that are not
  published yet —which is not what runs— or a published version, which cannot change because the cases that started
  on it keep running on it.
- A published version is the one thing that can be changed about a version: an administrator can **retire** it from
  that same banner. Nothing is deleted —the cases that started on it keep running on it— and the process falls back
  to the previous version that is still standing, or to none, which the page then says.
- A guest store only gets the diagram and the file of the version in force. The detail, the history and the versions
  all answer `404` to it, so the header falls back to the process that travels inside the diagram, and edit, publish
  and delete are hidden: a role in your own store is not a role in theirs. The process list shows what other stores
  share with you, which is how a guest reaches that page.

## The modeler

`/procesos/:id/modelar`, reachable from the process with the Model button; the old `/procesos/:id/editar-diagrama`
leads here. It is bpmn-js limited to what the model can hold.

- **The palette and the change-type menu** offer only what the model has, and in an executable process only what can
  run. The hard rules of the model are a `RuleProvider`: a sequence flow does not cross pools, a start receives
  nothing, an end emits nothing, and message flows only go between pools. They are plain functions with tests of their
  own.
- **The properties panel**, beside the canvas, edits what bpmn-js does not draw: the name, the condition and the
  default flow, the role of a lane, the content of a message, its channel, what happens if it fails, its variable and
  its correlation key, a timer in ISO 8601 and the process a call activity calls. What BPMN has no element for
  travels in the `bpm:` extension, so it survives the round trip.
- **It saves on its own** two seconds after the last change, one save at a time, with `If-Match` and an
  `Idempotency-Key`. A cut connection retries with the same key; a conflict or a file the API refuses stops the
  saving until someone decides. Each change also leaves a copy in IndexedDB until the server confirms it, offered back
  when the page opens again.
- **The diagnosis** comes back after each save, as markers on the elements and as a list beside the canvas, never as
  color alone; clicking a finding selects its element. Errors keep the Publish button of the modeler disabled.
  Right after publishing, the modeler sends the SVG of the new version (`PUT .../versiones/{n}/dibujo`), which the
  server cleans and keeps for the process manual; if it does not arrive, the manual says so.
- Undo and redo, a minimap, and a file in and out: a BPMN file from another tool replaces the drawing and the autosave
  sends it, and the drawing downloads as BPMN, SVG or PNG. A switch turns the process executable or documental.
- **The structure view** ([ADR-0060](../docs/adr/0060-structure-view.md)) is the same model as a tree beside the
  canvas —pools, lanes, nodes and their outgoing flows— following the WAI-ARIA tree pattern: the arrows move, F2
  renames, Delete deletes, and its actions create, connect and move a node to another lane. They run as bpmn-js
  commands, so they undo and save like a drag, and what the rules refuse is announced in an `aria-live` region. The
  context pad is raised to 24 × 24 px.
- **The bpmn.io logo stays visible**: panels sit beside the canvas, never on it, and an end-to-end test checks it.

### Who else is editing

[ADR-0061](../docs/adr/0061-presence-and-compare-ui.md), over the live events of the API:

- Opening the modeler asks for the lock. Whoever gets it edits, and everyone on the process shows as an avatar with
  their initials. Whoever does not get it sees the same drawing read-only, updated with every save, with who holds
  the lock and a button to take control.
- Losing control loses no work: the autosave stops and what was not saved stays in the local copy.
- A save that finds a newer one (`412`) offers to compare before reloading: the differences between the drawing on
  screen and what was saved, painted and listed.
- The lock is kept with a heartbeat every 20 seconds and let go when the page closes. The live connection is managed
  here, not by the retry of `EventSource`: on any error it closes and opens again after a wait that doubles from one
  second up to 30, with some randomness, so a server that comes back is not hit by every tab at once.

## Comparing

`/procesos/:id/comparar` takes two states of a process —any version, the one in force or the draft— and draws the
second with what changed: added, changed and moved, each with its own line style, and a legend in words that says
it again, with a count of each kind and each group sorted by name. The legend is also the only place that can say
what was removed, since it is not in the drawing. Color is never the only signal. The modeler and the page of a
request use the same legend component.

## Review, comments and notifications

[ADR-0074](../docs/adr/0074-review-comments-and-notifications-ui.md), over the API of comments and notifications and of
review and approval:

- **Asking for review.** When the organization publishes with approval
  (`GET /empresas/actual/politica-de-publicacion`), the process page and the modeler show *Ask for review* instead of
  Publish, or *Review waiting* with a link to the open request. The modeler saves what is left before asking, since
  the API freezes what is saved; like Publish, it waits for a saved draft, and a failed save stops it with the
  reason. `/politica-de-publicacion` is where the administrator chooses directly or with
  approval, how many approvals, and a list of people (read-only ones cannot approve) or a process role.
- **`/solicitudes`** is the inbox: by default what waits for the person's decision, and in the other tab every
  request of the organization, filtered by state; the tab and the state live in the address. **`/solicitudes/:id`**
  draws the frozen draft (read with `para=dibujar`, so viewing is enough) with the markers of the comparison and
  lists the same difference with the comparison legend, so what was removed is said too. An approver who has not
  decided approves, asks for changes or rejects, the last two with a reason; the requester or whoever administers the
  process withdraws it, after confirming.
- **Comments** sit in a panel beside the canvas, in the modeler and on the process page: the threads of the selected
  element, or of the whole process when nothing is selected, in the draft or in the version on screen. The canvas
  draws over each element a button with its open threads, which selects it. Replies, resolve and reopen, edit and
  delete (asked twice) are in each thread. The text is shown only as the HTML the API cleaned. A link with
  `?comentario=`, which the bell and the e-mails use, opens the thread's version, selects its element and focuses
  the thread; `?solicitud=` goes to the request.
- **Mentions** are a WAI-ARIA combobox that asks `GET /procesos/{id}/mencionables` 250 ms after the last key: the
  arrows move, Enter chooses, Escape closes, and each chosen person is a chip with a button to remove it. The state
  lives in `SelectorDeMenciones`, a plain class with its tests: it never offers who is already chosen and stops at 20.
  Typed and immediate searches share one `switchMap`, and an answer for other text or after the field lost focus is
  dropped.
- **The bell** in the header arrives when the browser is idle. Its number is the count the notification stream sends
  (`/notificaciones/flujo`), on opening and on every change, so it needs no polling and a reconnection catches up by
  itself; the stream reconnects like the modeler's. While it is open, marking one as read leaves the count to it. It opens a panel with the latest 20, each linking to what it
  points to, mark one or all as read, and a link to `/cuenta/notificaciones`, where each type is sent at once, in the
  daily digest or never. A new notification is read out in an `aria-live` region, and the button says the number in
  words.

## The read-only portal

[ADR-0075](../docs/adr/0075-read-only-portal.md): what is published, for the people who read processes, with nothing
that changes anything.

- **`/portal`** lists the published processes of a folder (that folder and the ones inside it, by name) beside the
  folder tree, which here has no controls to change it, or what the search finds. The search is the API of the global
  search through `PortalService`, without comments, which are not what is published; an element only in the draft
  opens its process without choosing it. The folder, the text and the page live in the
  address. Each process says its classification and its next review, or *Review overdue* when the API says so.
- **`/portal/procesos/:id`** opens one process with every read at once (`PortalService.abrir`): the detail, which
  names the owner (`duenoNombre`), the permissions, the characterization sheet, the documentation, the documents, the
  manuals and the BPMN file of the version in force, read with `para=dibujar`. If one of the extras fails the page
  shows the rest; without the permissions it offers nothing.
- **The diagram** is the bpmn-js viewer with the elements that have something to read marked with a thicker line. A
  click on an element shows beside it its documentation, only as the HTML the API cleaned, and its documents, each
  downloaded from a signed link asked for at the click; nothing chosen is the whole process. The structure view walks
  the same elements with the keyboard in read-only mode, where Enter chooses, and the choice is announced. A link
  with `?elemento=`, which the search uses, opens the process with that element chosen.
- **The manual** of the version in force: the newest one ready, in the reader's language if there is one, as PDF or as
  a web page. Asking to build one is offered only when the permissions say `EXPORTAR` and the role may send changes
  (`controlesDelPortal`), and the page asks again every two seconds until the queue finishes it.

## Administration

Six screens that used to need Postman. The navbar shows the administration ones only to an administrator, taking
the role from the observable rather than from a snapshot, because the navbar is built before anyone signs in.

- **Users** (`/usuarios`): search by name, sort by any of the three columns, add, change a role, take access away,
  bring somebody back, reset a password, and choose which process roles a person answers for, which is where their
  task tray comes from. Somebody added here gets a temporary password that the API invents and returns **once**; the
  screen shows it on its own and says so, because if it is lost the only way back is resetting it. Nobody is offered
  the button to deactivate themselves, because the API refuses it.
- **Taking access away is not deleting somebody.** They leave the list, which is the list of who can sign in, and
  **Show deactivated** brings them into view with a badge and a *Reactivate* button. The panel of process roles
  opens with the ones the person already answers for already ticked: saving sends the whole list, so opening it
  blank and saving would have taken every role away.
- **Process roles** (`/roles`): the list says which ones a lane is using, and Delete is disabled on those, because
  the API refuses them with a `409`. *Used by* opens the processes that use one, from `GET /roles/{id}`, each
  linking to its page.
- **Sharing** (`/procesos/:id/compartir`): by tax id, which is how a store is known to other stores. It is a
  read-only door to the version in force of that one diagram. What other stores share with you is listed on the
  process list, which is how a guest reaches it.
- **Account** (`/cuenta`): the store you belong to, with its name, its tax id, its contact and the day it was
  registered, from `GET /empresas/actual` —before, the card said "Store ID: 1", which tells nobody anything.
  Changing your own password closes every session of yours and opens a new one here,
  because that is what the API answers. Somebody who signed in with a temporary password is sent here by the route
  guard and kept here until they change it, since the API answers `403` to everything else.
- **Store settings** (`/configuracion`): who may change the structure of a diagram, and how long a session lasts
  without being used and after its sign-in. The simulation parameters are saved empty on purpose; they belong to
  running processes.
- **Store history** (`/historial`): everything that happened, paged, newest first. For administrators
  only, like the three above, because that is what the API allows.

## Operation

Five screens for a store that is running. They are the ones that turn the execution the API had into something
somebody can watch.

- **Cases** (`/casos`): every order running on a published version, filtered by process, state or the exact
  reference, and sorted by any of the three. Opening one by hand is a panel on the same screen: the process, the
  reference its messages will be matched by, and the variables the gateways will read. A process that starts with a
  message is not opened here, and the panel says so: its message opens it.
- **A case** (`/casos/:id`): the screen that answers *why is it stopped*. It draws the diagram of the version the
  case runs on, not today's working copy, from the file of that version: what it already went through and where its
  tokens are right now are markers, with the number of tokens on each node, and a pulse that stays still for whoever
  asks the system for less motion. Under it, the timeline, the variables, every step with its state, and the messages it
  sent and received with the reason one did not arrive. An administrator can replace the variables —what a case
  with no path was usually missing— and try again.
- **Task tray** (`/tareas`): what the open cases are waiting for somebody to do. It starts with the tasks of the
  process roles the person answers for, and says so when it is empty, because someone with no roles has no tray.
  Completing a task takes free key-value data, which lands in the variables of the case under
  `tarea.<nameInCamel>`, so a gateway further on can ask for it.
- **Simulation** (`/simulacion`, administrators): the clock, a batch of orders from the simulated customer, and how
  the partners answer —the seed, the rates and the ticks each one takes. Moving the clock writes on the same row as
  the settings, so the panel reads them again right after a tick; otherwise saving the partners next would answer
  `409`.
- **Dashboard** (`/tablero`): the same numbers for one process or for the whole store. Cases by state, how long a
  finished order took in ticks (average and p95, both made only of the finished ones), the work waiting in each
  tray, the messages that went out and came in, and what did not go as expected.

The drawing is the viewer's, in its `caso` mode. A case and a diagram have to be the same drawing, and the only thing
a case adds is where it is.

The free key-value editor —variables, what a task hands over, what every order of a batch carries— reads what was
typed: a number stays a number, `true` and `false` are booleans, something that starts with a brace is parsed as
JSON, and the rest travels as text. Gateway conditions compare numbers, so guessing wrong there is not cosmetic.

The timeline of a case shows the kind of every line in English and the sentence as the API writes it, in Spanish,
because those sentences carry names and numbers inside. It is the same decision the diagnosis of the modeler took,
and the same place to fix it: the API.

## In a container

`frontend/Dockerfile` compiles the app with Node and serves it with NGINX; Node only exists in the build stage. The
runtime image is `nginx-unprivileged`, so it runs as a normal user like the API image does, and therefore listens
on 8080 rather than on 80, which would need root.

`nginx.conf` does three things: serves any path that is not a file as `index.html`, because Angular resolves its
own routes; forwards `/api` to the API inside the compose network, which is why `environment.ts` has no absolute
URL and there is no CORS to configure; and answers `/healthz`, which is what the container health check asks, so it
reports on NGINX and not on the API, which has its own probe.

Files built with a hash in the name are cached for a year and `index.html` is not cached at all: otherwise a
browser would keep asking for the files of the version before.

```bash
docker compose up -d --build --wait   # from the root of the repository
```

That stack runs the API in its `prod` profile, so it comes up with no demo store: create one from the app. The
demo data is seeded by `dev`, which is what `npm start` talks to.

## Where things go

```
src/
├── app/
│   ├── bpmn/          the drawing on bpmn-js: the canvas, its modules, the properties panel, the structure view,
│   │                  the autosave, the live connection and the difference of a review request
│   ├── components/    components shared by several pages, such as the navbar
│   ├── pages/         one folder per page, with a components/ folder for pieces only that page uses
│   ├── models/        interfaces that mirror the API's DTOs, with the same field names
│   ├── service/       services that call the API and return observables
│   ├── helpers/       plain functions several screens share: API errors, free key-value data
│   ├── guards/        who may open a route
│   └── interceptors/  HTTP interceptors
├── environments/      the API URL for development and production
└── styles.scss        Bootstrap variables and global styles
```

## Conventions

- **Every screen is its own bundle.** The routes load their component with `loadComponent`, so opening the app
  downloads the shell, the front page and the login, and a screen arrives when somebody goes to it. The initial
  bundle went from 1.03 MB to 771 kB with that one change. Today the build fails above 800 kB, and bpmn-js, the
  largest library of the app, arrives only with a screen that draws.
- Services return typed observables, also for create and delete. A component updates its list only after the API
  confirms the change.
- No nested subscriptions: `switchMap` for route parameters and `forkJoin` for parallel calls. Subscriptions end with
  `takeUntilDestroyed()` or the `async` pipe.
- Create and edit screens use reactive forms with `Validators`.
- Unique elements carry an `id` and repeated ones a `data-testid`, so browser tests find them without relying on styles
  or on the page structure.
- **What sorts a table is a button**, styled to look like a column title, with `aria-sort` on the cell and an arrow
  that says which way it goes. A `(click)` on the `<th>` works with a mouse and does not exist for a keyboard.
- Vitest covers the logic that does not need a screen, written as plain functions so it can be tested alone. The
  screens are tested end to end, in [`../e2e/`](../e2e/), with a real browser against the stack of containers.
- Enum values are turned into lists with `Object.keys` and typed, so a template can index the map of names without
  a cast. There is no `$any` in a template.

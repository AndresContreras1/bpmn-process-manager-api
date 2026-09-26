# BPMN Process Manager web

The Angular front end of the [BPMN Process Manager API](../README.md). Each online store signs in, manages its
processes and views their BPMN diagrams. Screens arrive in small pull requests; so far the app has the home page, sign
in, store registration, the account page, the process screens and the diagram viewer.

## Stack

- Angular 19 with standalone components, `inject()` and the built-in control flow (`@if`, `@for`).
- Bootstrap 5.3, customized through its Sass variables, and Font Awesome icons.
- RxJS and `HttpClient` to talk to the REST API.

## Run it

Requirements: Node.js 22 or later (Angular 19 warns on Node 24 but builds with it) and the backend running on port
8080 (see the [root README](../README.md#getting-started)).

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

A production build goes to `dist/bpmn-process-manager-web/browser/`:

```bash
npm run build
```

## Session

- Signing in stores both tokens and the user they belong to in `localStorage`, behind a `TokenService`. The demo
  store signs in with `admin@demo.com` / `admin123`.
- An HTTP interceptor adds `Authorization: Bearer <token>` to every call except sign in and store registration.
- **The session renews itself.** The access token lives fifteen minutes, so a `401` does not mean the session is
  over: the interceptor exchanges the refresh token for a new pair and retries the request. The user sees nothing.
  The sign-in page comes back only when there is no refresh token left or when the renewal is itself rejected, and
  then it says why (`?sesion=vencida`).
- A refresh token works **once**, and sending it twice closes the session on purpose, as reuse detection. So the
  renewal is shared: every request that expires at the same moment waits for the same call. Opening a process runs
  two requests in parallel, and both are served by one renewal.
- A session counts as open while the refresh token exists, not while the access token is valid. Tying the route
  guard to the access token would throw the user out fifteen minutes in, which is the same bug seen from the
  other side.
- Every POST made with a session carries an `Idempotency-Key`. The API replays a repeated key instead of creating a
  second resource, which matters for the retry after a renewal and for a double click. The header is set before the
  request is signed, so the retry carries the same key.
- Signing out sends the refresh token, so the API closes that session instead of leaving a token that still renews.
- A route guard keeps private pages behind an open session and remembers the page the user asked for.
- `AuthService` publishes the current user through a `BehaviorSubject`, so the navbar and the pages react to sign in
  and sign out.

## Processes

- The list shows the pages of 10 processes the API returns, the most recently changed first. Clicking a column title
  sorts by it (the `orden` parameter), and clicking it again reverses the order. The search box waits until the user
  stops typing and cancels the previous request (`debounceTime` and `switchMap`); the state and category filters work
  the same way, and clicking a category filters by it.
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
- Buttons follow the user's role: administrators and editors create, edit and publish, only administrators delete,
  and read-only users just browse. The API enforces the same rules and answers `403` otherwise.

## Diagram viewer

The detail page asks for the process and for its whole diagram in parallel (`forkJoin` with
`GET /api/v1/procesos/{id}/diagrama`). If only the diagram fails, the rest of the page still shows.

- The diagram is plain SVG drawn by the `diagrama-bpmn` component, without a BPMN library. `lienzo.ts` places every
  element first, and the component only draws what it receives.
- Events follow the notation: a thin circle where the process starts, a double one where it waits for a message, a
  thick one where a path ends, and an envelope on the three that carry a message, filled only on the one that sends
  it. They take part in the layout like any other node, so they widen the pool and raise their lane.
- Customers sit on top, the store in the middle with its lanes, and the other participants below; black-box pools
  show only their name.
- Each task and gateway takes its x from `posicionX`, shared by every lane so the flow reads from left to right, and
  its y from `posicionY` relative to its lane, so a node never leaves its lane and each lane grows to fit.
- Sequence flows are drawn with right angles and their labels. Message flows are dashed and numbered, and the list
  under the diagram gives each one its content and correlation key.
- A message flow leaves the node it is anchored to and enters the node that waits for it, rather than running from
  the border of one pool to the border of the other: which node sends and which one waits is half of what a message
  flow says. When both ends are anchored to nodes at different x it turns through the gap between the pools. An end
  inside a black box has no node, and keeps the border.
- Clicking a task, a gateway or an event, or focusing it and pressing Enter, opens its details: lane, role,
  description, the flows that come in and go out with their conditions, and what it sends and waits for, each with
  the number the diagram draws on it. A message there says how it travels, what the process does if it cannot be
  delivered, the fields it carries, the variable its body becomes and how it is matched to a case.
- The field types are translated, like the change history: the API names them in Spanish.

### Versions and who is looking

- Publishing freezes the diagram as a version, so the page asks for the versions of the process and lets you pick
  one. A frozen version is read from `GET /procesos/{id}/versiones/{numero}/diagrama`, which answers the same shape.
- The page says which of three cases you are in: the working copy, the working copy with changes that are not
  published yet —which is not what runs— or a published version, which cannot change because the cases that started
  on it keep running on it.
- A guest store gets one door only: `GET /procesos/{id}/diagrama`. The detail, the history and the versions all
  answer `404` to it, so the header falls back to the process that travels inside the diagram, and edit, publish and
  delete are hidden: a role in your own store is not a role in theirs. There is no screen yet that lists what other
  stores share with you, so a guest reaches that page by URL; the list belongs to the sharing work of F6.

## Diagram editor

`/procesos/:id/editar-diagrama`, reachable from the process with the Model button. A role that can only read sees
the same screen without the palette, the forms or the buttons.

- **The canvas is the viewer's**, with an `editable` input. Two components would have meant two drawings of the same
  diagram, and they would have drifted.
- **The outline on the left** lists participants, lanes, the nodes inside each lane and the message flows. The canvas
  can only be clicked on nodes, so everything else is picked there. Lanes move up and down with buttons: the whole
  order is sent, and a button can be reached with a keyboard.
- **One reactive form per kind**, with the exact fields of its request and `version` hidden — never shown, never
  typed, sent back as it arrived, which is what the API compares. Moving a node between lanes is a field in its
  form, and the lanes offered are the ones of its own participant.
- **Dragging** a node saves where it was dropped, and dropping it on another lane changes the role that does the
  work. Turning a pixel into the position the API stores means undoing the arithmetic the drawing did, so `lienzo.ts`
  publishes the origins it used. The canvas listens to pointer events, so a finger works and a synthetic mouse drag
  does not.
- **Connecting** asks for two nodes: same participant, sequence flow; different participants, message flow anchored
  to both. A sequence flow never crosses from one pool to another.
- **The diagnosis is asked for again after every change**, together with the diagram, through one subject with a
  debounce. Errors block publishing and warnings do not, so the Publish button is disabled while there is an error.
  Clicking a finding selects the element it is about: that needs the type as well as the id, because ids repeat
  across tables, and the type comes from the word the API puts in front of the name.
- **Deleting asks first what would be left.** The API can diagnose the diagram without a given element, and the
  confirmation says "takes the diagram from 2 to 13 errors", because deleting a node takes its flows with it.
- **The second opinion** from the model sits next to the diagnosis, marked as advice that changes nothing. With no
  key configured the API answers `503` and the panel says the review is not set up here.
- The findings keep the API's wording, in Spanish; only the class in front of the name is translated, because the
  sentences carry data inside them.

## Where things go

```
src/
├── app/
│   ├── components/    components shared by several pages, such as the navbar
│   ├── pages/         one folder per page, with a components/ folder for pieces only that page uses
│   ├── models/        interfaces that mirror the API's DTOs, with the same field names
│   ├── service/       services that call the API and return observables
│   └── interceptors/  HTTP interceptors
├── environments/      the API URL for development and production
└── styles.scss        Bootstrap variables and global styles
```

## Conventions

- Services return typed observables, also for create and delete. A component updates its list only after the API
  confirms the change.
- No nested subscriptions: `switchMap` for route parameters and `forkJoin` for parallel calls. Subscriptions end with
  `takeUntilDestroyed()` or the `async` pipe.
- Create and edit screens use reactive forms with `Validators`.
- Unique elements carry an `id` and repeated ones a `data-testid`, so browser tests find them without relying on styles
  or on the page structure.
- There are no unit tests in this project: end-to-end tests with Selenium will live in a separate `e2e/` project.
- Enum values are turned into lists with `Object.keys` and typed, so a template can index the map of names without
  a cast. There is no `$any` in a template.

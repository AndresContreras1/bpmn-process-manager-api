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

- Signing in stores the JWT and the user it belongs to in `localStorage`, behind a `TokenService`. The demo store
  signs in with `admin@demo.com` / `admin123`.
- An HTTP interceptor adds `Authorization: Bearer <token>` to every call except sign in and store registration.
  When the API answers `401`, it clears the session and goes back to the sign-in page.
- A route guard keeps private pages behind a valid session and remembers the page the user asked for.
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
- The detail page shows the process and its change history. Publishing and deleting ask first in a Bootstrap modal,
  and the page changes only with the API's answer. A published process cannot go back to draft.
- Buttons follow the user's role: administrators and editors create, edit and publish, only administrators delete,
  and read-only users just browse. The API enforces the same rules and answers `403` otherwise.

## Diagram viewer

The detail page asks for the process and for its whole diagram in parallel (`forkJoin` with
`GET /api/v1/procesos/{id}/diagrama`). If only the diagram fails, the rest of the page still shows.

- The diagram is plain SVG drawn by the `diagrama-bpmn` component, without a BPMN library. `lienzo.ts` places every
  element first, and the component only draws what it receives.
- Customers sit on top, the store in the middle with its lanes, and the other participants below; black-box pools
  show only their name.
- Each task and gateway takes its x from `posicionX`, shared by every lane so the flow reads from left to right, and
  its y from `posicionY` relative to its lane, so a node never leaves its lane and each lane grows to fit.
- Sequence flows are drawn with right angles and their labels. Message flows are dashed and numbered, and the list
  under the diagram gives each one its content and correlation key.
- Clicking a task or a gateway, or focusing it and pressing Enter, opens its details: lane, role, description and the
  flows that come in and go out, with their conditions.

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

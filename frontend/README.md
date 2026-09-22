# BPMN Process Manager web

The Angular front end of the [BPMN Process Manager API](../README.md). Each online store signs in, manages its
processes and views their BPMN diagrams. Screens arrive in small pull requests; so far the app has its layout and the
home page.

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

The app opens on http://localhost:4200. It calls the backend URL set in `src/environments/environment.development.ts`,
and the backend's CORS configuration already allows that origin.

A production build goes to `dist/bpmn-process-manager-web/browser/`:

```bash
npm run build
```

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

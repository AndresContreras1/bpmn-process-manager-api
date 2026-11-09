[← Decision records](README.md)

# ADR-0018: No Thymeleaf; a single-page app behind NGINX

- **Status:** Accepted
- **Decided in:** plan v3 (D18)

## Context

Stores need a web interface. Server-side templates would put pages inside the API, and a web app served from a
different origin than the API would need CORS between the two.

## Decision

The web app is Angular, and it is its own image: it is built with Node 22 (`node:22-alpine`) and served by NGINX
(`nginxinc/nginx-unprivileged`). For the browser, NGINX is the only door: it serves the compiled app, answers
`index.html` for any path that is not a file so that deep links work, and forwards `/api/` to the API container
inside the Compose network, with the request id it gives each request in `X-Request-Id`. There is no template engine
in the API.

## Consequences

- The browser never leaves the origin of the app, so nothing is cross-origin for it. Spring still checks the
  `Origin` header of each `POST` against `CORS_ALLOWED_ORIGINS`, so `compose.yaml` lists the site's own address
  there. The API keeps its own port for the Postman collection, curl and the load tests.
- The API stays a JSON API, and the web app is one more client of the same contract.
- The web container runs like the API's: unprivileged, on port 8080, with a read-only file system.
- Plan v5 keeps the single-page app, but will render e-mails (never pages) with Thymeleaf.

## Verification

- CI job *Web Image & Stack*: builds the web image and scans it with Trivy, brings up database, API and web, and
  checks that NGINX serves the app, that a deep link answers with the app and not a `404`, and that the API answers
  through the web container.
- CI job *E2E*: the Selenium suite of `e2e/` drives a headless Chrome against that stack, and reaches the API
  through the origin of the app.

[← Documentation](README.md)

# Security

## Roles and permissions

| Capability | Administrator | Editor | Read-only |
|---|:---:|:---:|:---:|
| View processes, process roles and diagrams | ✓ | ✓ | ✓ |
| Create and edit processes and diagrams | ✓ | ✓ | — |
| Create and edit participants and lanes | ✓ | ✓ or — | — |
| Delete processes and diagram elements | ✓ | — | — |
| Manage process roles | ✓ | — | — |
| Manage users | ✓ | — | — |
| Share a process with a partner company | ✓ | — | — |
| Watch cases, their timeline, the tray and the dashboard | ✓ | ✓ | ✓ |
| Open a case, complete and take a task, cancel a case | ✓ | ✓ | — |
| Correct the variables of a case and retry it | ✓ | — | — |
| Say which process roles a person belongs to | ✓ | — | — |
| Read the outbox and the inbox of a process | ✓ | ✓ | ✓ |
| Send a message to a process | ✓ | ✓ | — |
| Move the store's clock, watch its simulation and ask for a batch of orders | ✓ | — | — |
| Decide how the store's simulated partners behave | ✓ | — | — |

Participants and lanes are the one row the store decides: with the setting `politicaEstructura` on
`SOLO_ADMINISTRADOR`, only administrators create and edit them, and editors keep modeling everything inside a lane.
The default is `ADMINISTRADOR_Y_EDITOR`, which is the table above.

A store always keeps at least one active administrator, and nobody can deactivate their own account. Changing a
user's access level or deactivating them takes effect at once: their open sessions are closed. Changing a password,
or having it reset, closes them too.

## Authentication

1. `POST /api/v1/empresas` registers a store together with its first administrator.
2. `POST /api/v1/auth/login` checks the credentials through Spring Security's `AuthenticationManager` and opens a
   session with two tokens, which travel in cookies the page's JavaScript cannot read (D29):
   - The **access token** is a JWT signed with `JWT_SECRET` that expires after 15 minutes. It goes in
     `__Host-acceso`, for the whole API. As RFC 8725 asks, it says who issued it (`iss`), for whom (`aud`), has an
     id of its own (`jti`) and names in its header the key that signed it (`kid`). Its other claims are the email,
     `usuarioId`, `empresaId`, the role and the session (`sid`).
   - The **refresh token** is 256 random bits. It goes in `__Secure-refresco`, which only travels to
     `/api/v1/auth`. The database stores only its SHA-256 hash, so a copy of the database cannot open a session.

   Both cookies are `HttpOnly`, `Secure` and `SameSite=Lax`, and the body of the answer carries no token: it says
   who signed in and when the access expires.
3. The browser sends the cookies back by itself. The filter builds the principal from the claims of the access
   token, without a database query, and rejects the tokens of a closed session. `Authorization: Bearer` is left
   for API keys.
4. `POST /api/v1/auth/refresh` exchanges the refresh cookie for new cookies of the same session. Each refresh token
   works once. A refresh token that was already used means that a copy exists, so the whole session is closed.
5. `POST /api/v1/auth/logout` closes the session of the cookies and clears them, even with an expired access token.
   Deactivating a user or changing their role closes all of their sessions, so the old role stops working at once.

A browser sends cookies to their site whatever page asked for the request, so what changes something with the
session cookies, and the login itself, also has to send the CSRF token: the value of the `XSRF-TOKEN` cookie in the
header `X-XSRF-TOKEN`. Only pages of the app's own origin can read that cookie, so another site cannot send it.
The web app gets the cookie from `GET /api/v1/auth/csrf` when it starts, and Angular's `HttpClient` copies it into
every change. Without it the answer is `403` with the title "Sin token CSRF". A request without session cookies
has no session to borrow, and does not need it: the registration of a store, and API keys when they arrive.

Rotating the signing key does not close sessions: `JWT_PREVIOUS_SECRET` keeps accepting the tokens of the old key,
which nobody signs with any more, for the fifteen minutes they live ([runbook](runbook.md)).

Public endpoints are limited to store registration, login, renewal, logout, the CSRF token and the API
documentation (not published in `prod`). The role matrix in [Roles and permissions](#roles-and-permissions) is
defined in one place, the security configuration, and decides `401` or `403` before any controller runs.

## Login protection

An unknown email, a deactivated user and a wrong password get the same `401`, and `DaoAuthenticationProvider` spends
the time of a BCrypt comparison even when the email does not exist. After 5 failed attempts for an email from the same
address within 15 minutes, the login answers `429` with `Retry-After` and stops checking passwords until the oldest
attempt leaves the window. Because attempts are counted per email and address, an attacker elsewhere cannot lock the
real user out.

Failed attempts are counted in PostgreSQL, so every instance of the API counts the same ones and spreading the
attempts over several instances gives no extra tries. Each instance keeps the closed sessions in memory, which
is what lets the JWT filter run no SQL, and learns of a session closed in another one at once, through
PostgreSQL's `NOTIFY`; [Several instances](architecture.md#several-instances) has the detail.

## Headers and HTTPS

Every response tells the browser what it may do with it. The API answers JSON and never a page, so its policy
lets nothing load and nobody frame it. The web app, which NGINX serves, gets a policy that allows what the app is
made of and nothing else:

| Header | API | Web app |
|---|---|---|
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` | Everything from its own origin; scripts only the ones Angular hashed when it built the app; no plugins, no frames, no forms sent elsewhere |
| `Content-Security-Policy-Report-Only` | — | Trusted Types, in report mode: the console says what they would block, and nothing breaks |
| `Strict-Transport-Security` | A year, subdomains included, on what arrived over HTTPS | The same; a browser only honours it over HTTPS |
| `X-Content-Type-Options` | `nosniff` | `nosniff` |
| `X-Frame-Options` | `DENY` | `DENY` |
| `Referrer-Policy` | `no-referrer` | `same-origin` |
| `Permissions-Policy` | No camera, microphone, location, payments, USB or motion sensors | The same |
| `Cross-Origin-Opener-Policy` · `Cross-Origin-Resource-Policy` | `same-origin` | `same-origin` |

The hashes of the scripts come from the build. Angular's `autoCsp` leaves two small scripts in the index, the one
that loads the bundles and the one that turns the stylesheet on, and hashes them; the image of the web app copies
those hashes into the header NGINX sends, so a change to the index changes them by itself. Two exceptions are
written down rather than hidden: the styles of the web app allow `'unsafe-inline'`, because Angular puts the
styles of each component in the page when it draws it, and Swagger UI, which only exists outside `prod`, gets a
policy that lets it load its own scripts and styles.

Behind the proxy, `prod` believes `X-Forwarded-For` and `X-Forwarded-Proto` only from addresses of the internal
network (Tomcat's `RemoteIpValve`): the login limit counts the client and not the proxy, and what arrived over
HTTPS gets HSTS. That is why the Compose stack publishes the API's own port only on `127.0.0.1`, and why in
production nothing but the proxy reaches the API. With a CDN in front, the proxy has to write the client it
trusts into `X-Forwarded-For`.

A test reads the headers of every route of the API, the pipeline reads those of every kind of response of the web
container, a missing file included, and the end-to-end tests fail if the browser's console reports something the
CSP blocked.

## Data isolation

A platform that hosts many stores must never show one store's data to another. The design enforces this instead of
relying on each query to remember a filter:

```java
@NoRepositoryBean
public interface RepositorioTenant<T extends EntidadEmpresa> extends JpaRepository<T, Long> {
    Optional<T> findByIdAndEmpresaId(Long id, Long empresaId);
    List<T> findAllByEmpresaId(Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
```

- Every tenant repository extends `RepositorioTenant`, and ArchUnit fails the build if a service calls the unfiltered
  `findById` or `findAll`.
- Ids that arrive in a request body are resolved the same way. A lane cannot point to another store's process role,
  and a sequence flow cannot connect another store's nodes.
- No request DTO carries an `empresaId`, which ArchUnit also checks. The client never chooses the tenant, and a
  request body that still sends one is rejected with `400`.
- Access to another store's resource answers `404`, not `403`, so the API does not confirm that the resource exists.
- An integration suite creates two stores and has one try to read, change or link the other's resources (50 cases).

**Read-only sharing (HU-23).** An administrator can share a process with another store by its NIT. A process then has
two doors: the write door finds only the store's own processes, and the read door also finds the ones shared with it.
The whole diagram is the only endpoint behind the read door, and it is marked `compartido: true` for the guest. The
detail, the history and every modeling endpoint stay private to the owner, and any change from the guest answers
`404`. The guest lists what it received in `GET /api/v1/procesos/compartidos-conmigo`, and the owner's process history
records when a process was shared and when the sharing ended.

## Passwords

A user can be created without one: `POST /api/v1/usuarios` then answers `claveTemporal`, which is the only time it
is ever shown, and `debeCambiarClave: true`. Whoever signs in with it can only call `POST /api/v1/auth/password`,
`logout` and `refresh`; anything else answers `403` with "Debe cambiar su contraseña antes de seguir.".

`POST /api/v1/auth/password` takes the password in use and the new one. It closes every session of that user,
this one included, and opens a new one with new cookies.
`POST /api/v1/usuarios/{id}/restablecer-clave` does the same from the other side, for an administrator, and answers
another temporary password.

The database never keeps a temporary password in the clear, and whoever creates the user passes it along by
whatever means they have. The e-mail is the other way in:

- **Verifying the e-mail.** Registering a store sends its first administrator a link that verifies their e-mail;
  `POST /api/v1/auth/verificacion` sends another. Until their e-mail is verified, a user cannot invite anybody.
- **Inviting by e-mail (HU-02.1).** `POST /api/v1/usuarios/invitaciones` sends a link with the access role of
  the invitation. Whoever follows it chooses their name and password, and joins with the e-mail already verified.
- **Recovering the password.** `POST /api/v1/auth/recuperacion` sends a link to choose a new password if the
  e-mail belongs to an active user, and answers the same `202` either way, so it tells nobody which e-mails are
  registered. Using the link closes every session of the user and voids the other recovery links.

Every link works once and expires: 48 hours to verify, 15 minutes to recover, 7 days to accept an invitation. Its
token is 256 random bits; the database keeps only its SHA-256, and the token in the clear exists only in the
e-mail. The link carries it after `#`, which a browser never sends to a server, so it is not written in the log
of NGINX or of anybody else. A link used, expired or made up gets the same `400`, "Enlace no válido". The e-mails
leave through the queue of jobs, in the transaction that asks for them, and are written in the language of the
request: Spanish, English or French.

## Supply chain

What the product is built from is checked as carefully as what it does. Each control answers a way a dependency,
a build step or a leaked key has compromised other projects:

| Control | What it prevents |
|---|---|
| Every GitHub Action pinned to a commit SHA, and a CI step that fails otherwise | A tag is a pointer its owner can move to other code; a 40-character SHA cannot be moved. That is how 76 tags of `trivy-action` ran a credential stealer in March 2026 |
| Each job gets a read-only token and asks for more in its own block; checkouts do not keep the token | A compromised step can only write where its job writes, and cannot read the token from the repository's git configuration |
| Dependabot for Maven, npm, GitHub Actions, Docker and Compose, weekly, with a seven-day cooldown | Dependencies fall behind silently; a release younger than a week is not offered, because most malicious ones are found and pulled within that time |
| Base images pinned by digest as well as by tag | The same tag is rebuilt over time; a digest changes only through a pull request |
| CodeQL over the Java, the TypeScript and the workflows | Injection, unsafe deserialization and similar bugs in the code, and script injection in the workflows |
| gitleaks over every commit | A key that reaches a commit stays in the history even after the file is fixed |
| Trivy over the API and the web images | Known vulnerabilities in the operating system of each image and in every library inside the jar. A critical one that already has a fix fails the pipeline; every high and critical finding goes to the Security tab |
| A CycloneDX SBOM of the API and of the web app | Answering "are we affected?" the day a new vulnerability is published. The API's travels inside the jar (`META-INF/sbom/application.cdx.json`); both are kept as artifacts of each run |

Values that look like secrets and are not, such as the signing key of the unit tests, are allowed by value in
`.gitleaks.toml`, never by path, so a real key in the same file would still be found.

A major version of a base image is a decision rather than an update, so Dependabot only offers the rebuilds
and the minor releases of the images: moving to the next Java or Node line, or to a new PostgreSQL, comes in
a pull request of its own.

## Reporting a vulnerability

[SECURITY.md](../SECURITY.md) says how to report one privately, what is in scope and how soon the answer comes.
The web app serves the same contact at `/.well-known/security.txt`, as RFC 9116 asks, so a researcher who only
knows the address of a deployment can find it. The file has an expiry date: the pipeline checks that NGINX serves
it and starts failing a month before it expires, so it is renewed instead of served stale.

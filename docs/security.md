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
     It lives the store's idle timeout, and never past the end of the session.

   Both cookies are `HttpOnly`, `Secure` and `SameSite=Lax`, and the body of the answer carries no token: it says
   who signed in and when the access expires. With a [second factor](#second-factor), the password opens only half
   of the login.
3. The browser sends the cookies back by itself. The filter builds the principal from the claims of the access
   token, without a database query, and rejects the tokens of a closed session. `Authorization: Bearer` is left
   for API keys.
4. `POST /api/v1/auth/refresh` exchanges the refresh cookie for new cookies of the same session. Each refresh token
   works once. A refresh token that was already used means that a copy exists, so the whole session is closed.
5. `POST /api/v1/auth/logout` closes the session of the cookies and clears them, even with an expired access token.
   Deactivating a user or changing their role closes all of their sessions, so the old role stops working at once.

A session has two limits, as NIST SP 800-63B-4 asks for AAL2, and each store chooses them in its settings
(`PUT /api/v1/empresas/actual/configuracion`). Without being renewed it lasts the **idle timeout**, from 30 to 60
minutes, an hour by default; whatever happens, it ends the **maximum duration** after its login, from 1 to 24 hours,
a day by default, and the user signs in again with their password. The API sees a session only when it renews,
every fifteen minutes while it is used, which is why the idle timeout starts at 30. Near its end, neither the
access token nor the cookies outlive the session, and a shorter duration reaches the sessions already open at
their next renewal.

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

## Second factor

A person can add an authenticator app to the password (TOTP, RFC 6238: HMAC-SHA1, 30-second steps, 6 digits):

1. `POST /api/v1/auth/segundo-factor/totp` answers a new secret, in Base32 and as the `otpauth://` URI of the QR
   code. It is shown once and is not active yet.
2. `POST /api/v1/auth/segundo-factor/totp/confirmacion` takes a code the app shows now and turns it on. The answer
   carries ten recovery codes, also shown once, each good for one sign-in without the phone.
3. From then on, `POST /api/v1/auth/login` with the right password answers `segundoFactorPendiente: true`, an access
   cookie of five minutes and no refresh cookie. That token works only for
   `POST /api/v1/auth/segundo-factor/verificacion`, logout and the CSRF token; anything else answers `401` with the
   title "Segundo factor requerido". The code, or a recovery code, opens the session with both factors in the
   token (`fac`), and the half-done token stops working.

What makes it hard to abuse:

- A code of the app is accepted in its step and one step either side, for the clock of the phone, and never twice:
  the step of the last accepted code is kept, and the update that moves it forward only moves it forward, so the
  same code sent twice at once opens one session. Each recovery code works once, written with or without its dash.
- Wrong codes count with the limit of the login, in the same account: 5 in 15 minutes and the answer is `429`.
- The secret is stored encrypted with AES-256-GCM, with the id of its user as associated data, so a secret copied to
  another row does not decrypt. Recovery codes have 50 bits and are stored as HMAC-SHA256: without the key, which
  is not in the database, a copy of the table cannot be searched. Both keys come from `MFA_CLAVE_DE_CIFRADO`.
- Enrolling, confirming, removing it and renewing the codes ask for a recent verification, as closing the store
  does: the factors of the session are dated in the token (`auth_time`), and older than ten minutes the answer is
  `403` "Verificación requerida". `POST /api/v1/auth/reautenticacion` confirms again: with the code when there is a
  second factor, since the password alone would prove nothing, and with the password otherwise.
- Nothing under `/api/v1/auth` is kept in the idempotency keys, so a secret or a code never reaches the database
  in the clear. Changing the password keeps the factors of the session.
- An administrator can remove the second factor of someone who lost the phone and the codes
  (`DELETE /api/v1/usuarios/{id}/segundo-factor`, with a recent verification), which closes that person's sessions.
  Nobody removes their own that way: it goes from their account, with a verification of their own.

Each store chooses who has to use one (`politicaSegundoFactor` in its settings): nobody, as by default, the
administrators, or everybody. Whoever needs one and has none still signs in, but can only enroll it: anything
else answers `403` with the title "Segundo factor obligatorio". Every token of a full session is decided when it is
issued, so renewing after the confirmation is enough to work, and a store that starts asking for it reaches the
open sessions at their next renewal.

### Passkeys

A passkey (WebAuthn) is a pair of keys kept by the person's device or password manager: the API keeps only the
public key and checks with it what the device signs. Every ceremony asks the device to verify the person
(fingerprint, face or PIN), so a passkey is two factors at once: it works as the second factor after the password
and alone to sign in without one. [ADR-0049](adr/0049-passkeys-without-http-session.md) explains why the API uses
Spring Security's WebAuthn operations from its own controllers.

Each ceremony has two steps: the API answers options with a challenge, the browser hands them to
`navigator.credentials`, and the API checks what the device signed for them.

| Ceremony | Options | Answer |
|---|---|---|
| Register one, with a recent verification | `POST /api/v1/auth/segundo-factor/passkeys/opciones` | `POST /api/v1/auth/segundo-factor/passkeys` |
| Finish the half-done login | `POST /api/v1/auth/segundo-factor/verificacion/passkey/opciones` | `POST /api/v1/auth/segundo-factor/verificacion/passkey` |
| Confirm who you are | the same options | `POST /api/v1/auth/reautenticacion/passkey` |
| Sign in without a password | `POST /api/v1/auth/passkeys/opciones` | `POST /api/v1/auth/passkeys/entrada` |

What makes it hard to abuse:

- The challenge lives in `desafios_webauthn`, hashed: it works once, for its purpose and its person, and for five
  minutes. What the device signed is checked against the origin of the web app (`WEBAUTHN_ORIGINS`), the RP id
  (`WEBAUTHN_RP_ID`), the challenge, the user verification flag and the public key kept at registration.
- The counter of signatures only goes up: the update that stores a new one moves it forward or changes nothing, so
  the same signature used twice, or a copy of the key, signs in once.
- To finish a login or confirm who someone is, only the passkeys of that person are offered and accepted. Each
  failure counts with the limit of the login in that account; signing in without a password counts against the
  owner of a known passkey, and against the address in any case. A failure of a known passkey lands in the audit
  trail as `PASSKEY_FALLIDA`.
- Signing in without a password treats a deactivated person, an unknown passkey and a signature that does not
  verify the same way: `401`. A store that signs in only with Google or Microsoft does not take passkeys either,
  except from its administrators.
- The first second factor, app or passkey, brings the ten recovery codes; a second one keeps them. Removing the last
  one removes the codes too, and the login goes back to the password alone. Up to ten passkeys per person.
- The administrator who removes someone's second factor removes their passkeys too, and anonymizing a person
  deletes them and the user handle.

## Signing in with Google or Microsoft

`GET /api/v1/auth/proveedores` lists the configured providers, and the web app sends the browser to
`GET /api/v1/auth/oidc/{proveedor}`. Spring Security does the OpenID Connect authorization code flow:

1. The request to the provider carries a `state`, a `nonce` and a PKCE challenge (`S256`), also for confidential
   clients. The API has no HTTP session, so that request waits in PostgreSQL (`peticiones_oidc`), tied to the
   browser that started it by a cookie `__Host-oidc` of 256 bits of which only the SHA-256 is kept. It works once
   and for ten minutes: the address of the way back, passed to someone else, does not sign them in.
2. The way back to `/api/v1/auth/oidc/callback/{proveedor}` exchanges the code with the PKCE verifier and checks
   the `id_token`: its RS256 signature against the keys the provider publishes, its dates, its audience, its
   `nonce` and its issuer. For Microsoft's common tenants the issuer has to be the one of the organization the
   token names in `tid`. The access token of the provider is not kept anywhere.
3. Then the person is decided:
   - **A linked account** (`identidades_externas`, by provider and `sub`) signs in. With a second factor, the
     provider is only the first one: the browser lands on the half-done login, as with the password.
   - **An e-mail the provider did not verify** opens nothing (`email_verified`, or `xms_edov` for Microsoft).
   - **An e-mail that already has a user** is not linked by itself: the browser goes to `/vincular#token=...`,
     and `POST /api/v1/auth/identidades/vinculo` links it only with the password, or the code of the second factor
     when there is one. The token works once and for ten minutes, and wrong passwords and codes count with the
     login's limit.
   - **An e-mail of a verified domain** joins the store of that domain with its default role, with the e-mail
     already verified and a random password nobody knows.
   - **Anything else** goes back to the login with the reason.

The session that comes out is the same pair of cookies as the login, with the factor `PROVEEDOR_EXTERNO` in the
token.

A store proves it controls a domain by publishing a TXT record `bpmn-process-manager-verificacion=<token>`
(`/api/v1/empresas/actual/dominios`, administrators only), and a domain can be verified by one store only. A store
can also ask its people to sign in only with a provider (`soloEntradaExterna`): the password then answers `403` to
everybody but the administrators, who keep it so that a fallen provider does not lock the store out, and nobody
can unlink the last account they sign in with.

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

## Audit trail

Each store has an audit trail (`bitacora`) of what happened in it:

| Action | When |
|---|---|
| `SESION_INICIADA` | A session opens, by any way in, with its factors |
| `LOGIN_FALLIDO` · `CODIGO_FALLIDO` | A wrong password or second-factor code for an account of the store |
| `SEGUNDO_FACTOR_ACTIVADO` · `SEGUNDO_FACTOR_QUITADO` · `CODIGOS_DE_RECUPERACION_RENOVADOS` | The second factor changes |
| `USUARIO_CREADO` · `USUARIO_CAMBIADO` · `USUARIO_DESACTIVADO` · `CLAVE_RESTABLECIDA` · `USUARIO_ANONIMIZADO` | A person is created, changes role or status, gets a temporary password or is anonymized |
| `INVITACION_ENVIADA` · `USUARIO_UNIDO_POR_DOMINIO` · `IDENTIDAD_VINCULADA` · `IDENTIDAD_DESVINCULADA` · `DOMINIO_VERIFICADO` | Someone is invited, joins or links a provider |
| `CONFIGURACION_CAMBIADA` | The settings change, with the policies that resulted |
| `PROCESO_PUBLICADO` | A version is published |
| `TIENDA_DADA_DE_BAJA` · `BAJA_CANCELADA` | The store is closed or the closing is cancelled |

Each entry says who (a person, or the API), the action, the resource, whether it worked, the address, the browser,
the request id and the trace id, and a detail in JSON. It holds ids and never names or e-mails: nothing in it can
change, so nothing in it would have to be pseudonymized later.

What makes it hard to touch:

- No service writes it. Each one publishes an event that waits in Spring Modulith's outbox, in the same transaction
  as what happened, and the trail adds it after the commit: what is rolled back leaves no entry, and a restart does
  not lose one.
- Each entry has a number in the chain of its store and the SHA-256 of its fields together with the hash of the
  previous one. Adding takes a PostgreSQL lock per store, so two entries at once do not take the same number.
- A trigger turns down `UPDATE`, `DELETE` and `TRUNCATE`, for the API too.
- `GET /api/v1/auditoria/verificacion` recomputes the chain from its first entry and names the first one that was
  changed, deleted or put out of order.

The administrator reads it newest first, 50 at a time, with filters (`GET /api/v1/auditoria`), and exports it as CSV
or JSON (`/exportacion`); in the CSV, a cell a spreadsheet would run as a formula starts with an apostrophe. The trail
outlives its store, which stays as a tombstone. Anchoring the last hash of each day outside the database comes with
object storage (PR 61), and the role the API connects with gets only `INSERT` and `SELECT` on it with row-level
security (PR 51).

## Rate limiting

Every request that changes something spends a token of a bucket (Bucket4j) before it reaches anything. Each bucket
fills up again a token at a time over its period, and its state lives in PostgreSQL (`cubos_de_peticiones`), taken
with `SELECT ... FOR UPDATE`: every instance spends from the same bucket, and two requests at once do not spend the
same token.

| Class | What it covers | Counted by | Default |
|---|---|---|---|
| Signing in | Login, renewal, the code of the second factor, reauthenticating and confirming a link | Address | 30 a minute |
| Registration | Registering a store | Address | 10 an hour |
| E-mail | Verifying the e-mail, recovering the password and inviting | Address | 20 an hour |
| Changes | Every other `POST`, `PUT`, `PATCH` and `DELETE` | Person | 600 a minute |

Reading spends nothing. An empty bucket answers `429` with the title "Demasiadas peticiones" and `Retry-After` in
seconds. The limit of failed logins per e-mail still applies on top of the one per address. The AI review keeps
its own limit per store, which is what it costs.

A body over 1 MB answers `413` before it is read, and NGINX cuts at the same size in front of the API. When
`TURNSTILE_SECRET` is set, registering a store asks for Cloudflare Turnstile: the web app sends what the widget gave
it in `X-Turnstile-Token`, the API asks Cloudflare whether it is good for that address, and without it the answer
is `400`. If Cloudflare does not answer, the registration does not go through.

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

**Row-level security.** The database enforces the same isolation, so a query that forgets the filter still cannot
reach another store:

- Every table with `empresa_id` has row-level security, forced also for its owner, with a policy that lets a session
  see and write only the rows of `app.empresa_id` (V36). A test fails as soon as a table with `empresa_id` appears
  without it.
- The API works as `procesos_app`, which owns no table, is not a superuser and has no `BYPASSRLS`; on the audit trail
  it can only insert and read. Flyway migrates with its own connection, as the owner.
- Each connection the pool hands out takes that role, and before its first statement, and before any statement
  whose thread changed its scope, sets the scope in a single round trip. A filter sets the scope of each request as
  soon as it knows who makes it.
- What has no store yet looks a person up by e-mail or by token, and is named one by one as system scope: login,
  renewal, logout, registration, the e-mail links, confirming a provider link and the way back from a provider. What
  crosses stores on purpose says so where it starts: the purge, the job queue, the simulation clock (which then ticks
  each store in its own scope), the sessions closed in another instance and the demo data.
- A thread that does not say what store it is sees no row at all.
- A store invited to a process (HU-23) reads its published version and its diagram through policies of their own,
  over a `SECURITY DEFINER` function that returns only what was shared with it.

**Read-only sharing (HU-23).** An administrator can share a process with another store by its NIT. A process then has
two doors: the write door finds only the store's own processes, and the read door also finds the ones shared with it.
The whole diagram is the only endpoint behind the read door, and it is marked `compartido: true` for the guest. The
detail, the history and every modeling endpoint stay private to the owner, and any change from the guest answers
`404`. The guest lists what it received in `GET /api/v1/procesos/compartidos-conmigo`, and the owner's process history
records when a process was shared and when the sharing ended.

## Passwords

A password a person chooses follows NIST SP 800-63B-4. It needs at least 15 characters while it is the only
factor, and no other rule about what they are: capitals, digits and symbols made compulsory only lead to
predictable passwords, and a few words make a good one. The length counts real characters, so an emoji is one,
and the maximum is 72 bytes, all BCrypt reads: with accents or symbols a character takes more than one. It cannot
be one of the leaked or common passwords of `claves/filtradas.txt`, a character or a group of up to four repeated,
or a series like `abcdef`, and it cannot carry the name, the e-mail or the store of whoever chooses it, nor the
name of the service; the comparisons ignore case, accents and spaces. The rules hold wherever a person chooses a
password: registering a store, creating a user with one, accepting an invitation, changing their own and
recovering it, where a rejected password does not spend the link. Each rejection is a `400` that says why. The
list is short and written by hand; a larger one in the same format, one password per line, replaces it without
touching the code.

Every hash carries its algorithm in front, `{bcrypt}`, through Spring Security's `DelegatingPasswordEncoder`, and
the cost of BCrypt is `CLAVES_COSTO_BCRYPT`. When a login finds a hash of an older algorithm or a lower cost, it
rehashes the password it just checked, without touching the user's version; raising the cost, or moving to
Argon2id one day, forces nobody to change their password. The hashes from before the prefix match as BCrypt.

A user can be created without one: `POST /api/v1/usuarios` then answers `claveTemporal`, which is the only time it
is ever shown, and `debeCambiarClave: true`. Whoever signs in with it can only call `POST /api/v1/auth/password`,
`logout` and `refresh`; anything else answers `403` with "Debe cambiar su contraseña antes de seguir.".

A temporary password is 96 random bits written in 16 characters.
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

## The life of the data

**Closing a store.** An administrator closes their store with `POST /api/v1/empresas/actual/baja`, repeating its
name to confirm. For 30 days the store is read-only: its users sign in and read everything, any change answers
`409` with the title "Tienda dada de baja", its simulation clock stops, and an administrator can cancel the
closing with `DELETE /api/v1/empresas/actual/baja`. When the 30 days are over, the nightly purge deletes every row
of the store, table by table in the order of their foreign keys, the processes other stores shared with it
included, and leaves of its own row only the id and the dates of the closing and of the deletion. Its name, NIT
and e-mails are gone, so they can register again. `BorradoDeTiendasTest` checks against the catalogue of the
database that the deletion covers every table with `empresa_id` and respects every foreign key, so a new table
cannot be left behind.

**Anonymizing a person.** `POST /api/v1/usuarios/{id}/anonimizar` deletes a person's personal data on request,
and cannot be undone. Their user stays, because what they did names them, but under a pseudonym (`Persona
anonimizada 12`) and an address that is not theirs, deactivated, with no password that works, no sessions, no
process roles and no links; the store's history names them by the pseudonym too, and their e-mail can register
again. Nobody can anonymize their own account, and an anonymized user can no longer be edited, reactivated or
given a password.

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

## Reading XML

Every XML parser the code creates comes from `SecureXml`: StAX, DOM, SAX, `Transformer` and `SchemaFactory`. An
ArchUnit rule fails the build if anything else creates a factory, so no parser can be built without its flags:

| Control | What it prevents |
|---|---|
| DTDs forbidden, external entities off, `ACCESS_EXTERNAL_DTD`, `_SCHEMA` and `_STYLESHEET` empty, secure processing on, XInclude off | XXE: reading a file of the server, or making it call an internal address, through an entity, a schema, a stylesheet or an include |
| A limited StAX reader goes first and stops on the first DTD or entity | *Billion laughs* and the quadratic blow-up, which expand entities defined in the document itself |
| Limits before and while parsing: size, elements, depth, attributes per element, text length and time. BPMN: 5 MB, 100,000 elements, 64 levels, 64 attributes, 1 MB of text and 10 seconds | A document that is valid but built to exhaust memory or CPU |
| Only UTF-8 and UTF-16 | Rare encodings, the usual way of hiding a `DOCTYPE` from a filter |

A rejected document is a 400 with the reason. The tests send each attack while a local HTTP server listens, and
require that it receives no request.

## Reporting a vulnerability

[SECURITY.md](../SECURITY.md) says how to report one privately, what is in scope and how soon the answer comes.
The web app serves the same contact at `/.well-known/security.txt`, as RFC 9116 asks, so a researcher who only
knows the address of a deployment can find it. The file has an expiry date: the pipeline checks that NGINX serves
it and starts failing a month before it expires, so it is renewed instead of served stale.

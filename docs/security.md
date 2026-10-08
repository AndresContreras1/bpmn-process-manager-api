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
   session with two tokens:
   - The **access token** is a JWT signed with `JWT_SECRET` that expires after 15 minutes. Its claims are the email,
     `usuarioId`, `empresaId`, the role and the session (`sid`).
   - The **refresh token** is 256 random bits. The database stores only its SHA-256 hash, so a copy of the database
     cannot open a session.
3. Clients send `Authorization: Bearer <access token>`. The filter builds the principal from the claims, without a
   database query, and rejects the tokens of a closed session.
4. `POST /api/v1/auth/refresh` exchanges the refresh token for a new pair in the same session. Each refresh token
   works once. A refresh token that was already used means that a copy exists, so the whole session is closed.
5. `POST /api/v1/auth/logout` closes the session. Deactivating a user or changing their role closes all of their
   sessions, so the old role stops working at once.

Public endpoints are limited to store registration, login, token renewal, the API documentation (not published in
`prod`) and, in `dev`, the H2 console. The role matrix in [Roles and permissions](#roles-and-permissions) is defined in
one place, the security configuration, and decides `401` or `403` before any controller runs.

## Login protection

An unknown email, a deactivated user and a wrong password get the same `401`, and `DaoAuthenticationProvider` spends
the time of a BCrypt comparison even when the email does not exist. After 5 failed attempts for an email from the same
address within 15 minutes, the login answers `429` with `Retry-After` and stops checking passwords until the oldest
attempt leaves the window. Because attempts are counted per email and address, an attacker elsewhere cannot lock the
real user out.

Closed sessions and failed attempts are kept in memory, and closed sessions are reloaded from the database on
startup. A deployment with several instances would move both to a shared store such as Redis.

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
this one included, and answers a new session with its tokens: the ones that come back are the ones to keep.
`POST /api/v1/usuarios/{id}/restablecer-clave` does the same from the other side, for an administrator, and answers
another temporary password.

There is no email delivery: whoever creates the user passes the temporary password along by whatever means they
have. The database never keeps it in the clear.

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

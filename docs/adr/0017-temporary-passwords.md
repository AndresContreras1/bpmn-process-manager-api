[← Decision records](README.md)

# ADR-0017: Temporary passwords, changed before anything else

- **Status:** Accepted
- **Decided in:** plan v3 (D17)

## Context

An administrator creates their colleagues, and a password chosen by the administrator should not stay the
colleague's password. A forgotten password needs a way back in. There is no e-mail delivery: the specification does
not ask for one.

## Decision

- `POST /api/v1/usuarios` takes an optional `password`. Without it, the API generates a temporary password, answers
  it once as `claveTemporal`, keeps only its hash, and marks the user `debeCambiarClave`.
- `POST /api/v1/auth/password` takes the current password and the new one, closes every session of that user, this
  one included, and answers a new session with its tokens.
- `POST /api/v1/usuarios/{id}/restablecer-clave`, for an administrator, generates another temporary password, marks
  the user again and closes their sessions.
- While `debeCambiarClave` is set, the user can only call `/api/v1/auth/password`, `/api/v1/auth/logout` and
  `/api/v1/auth/refresh`; anything else answers `403` with "Debe cambiar su contraseña antes de seguir.".
  `CambioDeClaveFilter` decides it from the claims of the token, without a query.

## Consequences

- Whoever creates the user passes the temporary password along by whatever means they have. The database never keeps
  it in the clear.
- After a change of password no old session stays alive; the tokens that come back are the ones to keep.
- Passwords stop at 72 characters, which is what BCrypt reads.

## Verification

- `ContrasenasIntegracionTest`: the temporary password comes only in the response that creates it; with it the user
  can do nothing else until they change it; changing it closes the open sessions; a reset gives another temporary one
  and signs the user out everywhere.
- `CredencialesFueraDeLasRespuestasTest`: no response and no OpenAPI schema carries a password or its hash, and the
  temporary one appears only where it is generated.
- `AutorizacionPorRolTest`: resetting a password is the administrator's.
